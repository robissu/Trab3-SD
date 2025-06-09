package StableMulticast;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class StableMulticast {

    private static final String MULTICAST_ADDRESS = "230.0.0.1";
    private static final int MULTICAST_PORT = 4446;
    private static final int DISCOVERY_INTERVAL_MS = 2000; // Discover members every 2 seconds

    private int myId; // Unique ID for this instance (assigned based on discovery order)
    private String myIp;
    private Integer myPort;
    private IStableMulticast clientCallback;

    private volatile MulticastClock mc; // The vector of vector clocks
    private volatile List<StableMulticastMessage> messageBuffer; // Buffer for received messages
    private final List<InetSocketAddress> groupMembers; // IP and port of other StableMulticast instances

    private DatagramSocket unicastSocket;
    private MulticastSocket multicastSocket;

    private ExecutorService threadPool;
    private ScheduledExecutorService scheduledThreadPool;

    private volatile boolean running;
    private final Object clockLock = new Object(); // Lock for MC updates
    private final Object bufferLock = new Object(); // Lock for buffer updates

    private Scanner scanner; // For user input control

    public StableMulticast(String ip, Integer port, IStableMulticast client) throws IOException {
        this.myIp = ip;
        this.myPort = port;
        this.clientCallback = client;
        this.myId = -1; // Will be assigned during discovery

        this.messageBuffer = new LinkedList<>();
        this.groupMembers = Collections.synchronizedList(new ArrayList<>());
        this.mc = new MulticastClock(1); // Initialize with 1, will resize on discovery

        this.unicastSocket = new DatagramSocket(myPort, InetAddress.getByName(myIp));
        this.multicastSocket = new MulticastSocket(MULTICAST_PORT);
        this.multicastSocket.joinGroup(InetAddress.getByName(MULTICAST_ADDRESS));

        this.threadPool = Executors.newCachedThreadPool();
        this.scheduledThreadPool = Executors.newScheduledThreadPool(2); // For discovery and stabilization check
        this.running = true;
        this.scanner = new Scanner(System.in);

        startDiscoveryService();
        startUnicastReceiver();
        startStabilizationChecker();

        System.out.println("StableMulticast initialized. My IP: " + myIp + ", Port: " + myPort);
    }

    public int getId() {
        return myId;
    }

    private void startDiscoveryService() {
        scheduledThreadPool.scheduleAtFixedRate(() -> {
            try {
                // Send presence message
                String presenceMsg = myIp + ":" + myPort;
                byte[] buffer = presenceMsg.getBytes();
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length,
                        InetAddress.getByName(MULTICAST_ADDRESS), MULTICAST_PORT);
                multicastSocket.send(packet);

                // Listen for presence messages
                byte[] receiveBuf = new byte[256];
                DatagramPacket receivePacket = new DatagramPacket(receiveBuf, receiveBuf.length);
                multicastSocket.setSoTimeout(DISCOVERY_INTERVAL_MS / 2); // Shorter timeout for listening

                Set<InetSocketAddress> currentMembers = new HashSet<>();
                currentMembers.add(new InetSocketAddress(myIp, myPort)); // Add self first

                while (true) {
                    try {
                        multicastSocket.receive(receivePacket);
                        String received = new String(receivePacket.getData(), 0, receivePacket.getLength());
                        String[] parts = received.split(":");
                        if (parts.length == 2) {
                            String memberIp = parts[0];
                            int memberPort = Integer.parseInt(parts[1]);
                            InetSocketAddress memberAddress = new InetSocketAddress(memberIp, memberPort);

                            if (!memberAddress.equals(new InetSocketAddress(myIp, myPort))) { // Don't add self again
                                currentMembers.add(memberAddress);
                            }
                        }
                    } catch (SocketTimeoutException ste) {
                        break; // No more discovery messages for this interval
                    } catch (IOException e) {
                        System.err.println("Discovery error: " + e.getMessage());
                        break;
                    }
                }

                // Update group members and reassign IDs if necessary
                synchronized (groupMembers) {
                    List<InetSocketAddress> sortedMembers = new ArrayList<>(currentMembers);
                    Collections.sort(sortedMembers, (a, b) -> {
                        int ipComp = a.getAddress().getHostAddress().compareTo(b.getAddress().getHostAddress());
                        if (ipComp == 0) {
                            return Integer.compare(a.getPort(), b.getPort());
                        }
                        return ipComp;
                    });

                    boolean changed = !groupMembers.equals(sortedMembers);
                    if (changed) {
                        groupMembers.clear();
                        groupMembers.addAll(sortedMembers);

                        // Assign new ID to myself based on sorted list
                        this.myId = groupMembers.indexOf(new InetSocketAddress(myIp, myPort));
                        if (this.myId == -1) {
                            System.err.println("Error: My own address not found in sorted group members!");
                            return; // Should not happen
                        }

                        // Resize and initialize MC if necessary
                        synchronized (clockLock) {
                            if (mc.getMc().length != groupMembers.size()) {
                                System.out.println("Resizing MulticastClock from " + mc.getMc().length + " to "
                                        + groupMembers.size());
                                MulticastClock newMc = new MulticastClock(groupMembers.size());
                                // Copy existing clock values to new MC if possible
                                for (int i = 0; i < Math.min(mc.getMc().length, newMc.getMc().length); i++) {
                                    for (int j = 0; j < Math.min(mc.getMc()[i].length, newMc.getMc()[i].length); j++) {
                                        newMc.getMc()[i][j] = mc.getMc()[i][j];
                                    }
                                }
                                mc = newMc;
                            }
                        }
                        System.out.println("Group members updated. My ID: " + myId + ", Members: "
                                + groupMembers.stream().map(Object::toString).collect(Collectors.joining(", ")));
                        displayClockAndBuffer(); // Re-display on change
                    }
                }

            } catch (IOException e) {
                System.err.println("Discovery service error: " + e.getMessage());
            }
        }, 0, DISCOVERY_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private void startUnicastReceiver() {
        threadPool.execute(() -> {
            byte[] receiveData = new byte[4096];
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            while (running) {
                try {
                    unicastSocket.receive(receivePacket);
                    ByteArrayInputStream bis = new ByteArrayInputStream(receivePacket.getData());
                    ObjectInputStream ois = new ObjectInputStream(bis);
                    StableMulticastMessage receivedMsg = (StableMulticastMessage) ois.readObject();

                    processReceivedMessage(receivedMsg);

                } catch (SocketException se) {
                    if (running) { // Only print if not intentionally closed
                        System.err.println("Unicast socket closed unexpectedly: " + se.getMessage());
                    }
                } catch (IOException | ClassNotFoundException e) {
                    System.err.println("Error receiving unicast message: " + e.getMessage());
                }
            }
        });
    }

    private void processReceivedMessage(StableMulticastMessage msg) {
        synchronized (bufferLock) {
            messageBuffer.add(msg);
        }

        System.out.println("Received: " + msg);

        synchronized (clockLock) {
            // Check if senderId is valid within current group size
            if (msg.getSenderId() >= 0 && msg.getSenderId() < mc.getMc().length) {
                // MCi[j][*] <- msg.VC (update my view of sender's clock)
                mc.updateVector(msg.getSenderId(), msg.getSenderVC());

                // if i != j then MCi[i][j] <- MCi[i][j]+1
                if (myId != -1 && myId != msg.getSenderId() && myId < mc.getMc().length) {
                    mc.increment(myId, msg.getSenderId());
                }
            } else {
                System.err.println("Warning: Received message from unknown sender ID (" + msg.getSenderId()
                        + "). Discarding clock update.");
            }
        }

        // Deliver message to the upper layer
        clientCallback.deliver(msg.getContent());
        displayClockAndBuffer(); // Display after processing
    }

    private void startStabilizationChecker() {
        // Check for stable messages more frequently than discovery
        scheduledThreadPool.scheduleAtFixedRate(this::checkAndDiscardStableMessages, 1, 1, TimeUnit.SECONDS);
    }

    private void checkAndDiscardStableMessages() {
        synchronized (bufferLock) {
            synchronized (clockLock) {
                Iterator<StableMulticastMessage> iterator = messageBuffer.iterator();
                while (iterator.hasNext()) {
                    StableMulticastMessage msg = iterator.next();

                    int sender = msg.getSenderId();
                    if (sender < 0 || sender >= mc.getMc().length) {
                        System.err.println("Warning: Message in buffer has invalid sender ID (" + sender
                                + "). Skipping stabilization check.");
                        continue;
                    }

                    boolean isStable = true;
                    int minClockValueForSender = Integer.MAX_VALUE;

                    // Calculate min1<=x<=n(MCi[x][msg.sender])
                    for (int x = 0; x < mc.getMc().length; x++) {
                        if (x < mc.getMc().length && sender < mc.getMc()[x].length) { // Ensure indices are valid
                            minClockValueForSender = Math.min(minClockValueForSender, mc.getMc()[x][sender]);
                        } else {
                            // This can happen if group size changes dynamically while checking.
                            // Consider messages from unknown or outdated sender IDs as unstable for now.
                            isStable = false;
                            break;
                        }
                    }

                    if (isStable) {
                        // msg.VC[msg.sender] <= min1<=x<=n(MCi[x][msg.sender])
                        if (msg.getSenderVC()[sender] <= minClockValueForSender) {
                            System.out.println("Discarding stable message: " + msg.getContent() + " from P" + sender +
                                    " (VC[" + sender + "] = " + msg.getSenderVC()[sender] +
                                    " <= min(MC[x][" + sender + "]) = " + minClockValueForSender + ")");
                            iterator.remove(); // Discard msg
                            displayClockAndBuffer();
                        }
                    }
                }
            }
        }
    }

    public void msend(String msgContent, IStableMulticast client) {
        if (myId == -1) {
            System.err.println("Cannot send message: My ID is not yet assigned. Waiting for discovery...");
            return;
        }
        if (groupMembers.size() <= 1) {
            System.err.println("Cannot send message: No other members in the group yet.");
            return;
        }

        synchronized (clockLock) {
            // msg.VC <- MCi[i][*] (current sender's view of its own clock)
            int[] senderVC = mc.getVector(myId);
            StableMulticastMessage msg = new StableMulticastMessage(msgContent, senderVC, myId);

            // MCi[i][i] <- MCi[i][i]+1
            mc.increment(myId, myId);
            displayClockAndBuffer();

            // for all P do send(msg) to Pj enddo
            System.out.println("\nPreparing to send message: \"" + msgContent + "\"");
            System.out.print("Send to ALL members? (yes/no): ");
            String response = scanner.nextLine().trim().toLowerCase();

            if ("yes".equals(response)) {
                for (InetSocketAddress member : groupMembers) {
                    if (!member.equals(new InetSocketAddress(myIp, myPort))) { // Don't send to self
                        sendUnicast(msg, member);
                    }
                }
            } else {
                List<InetSocketAddress> recipients = new ArrayList<>();
                List<InetSocketAddress> otherMembers = groupMembers.stream()
                        .filter(m -> !m.equals(new InetSocketAddress(myIp, myPort)))
                        .collect(Collectors.toList());

                System.out.println(
                        "Choose recipients (enter numbers separated by space, or 'all' for all, 'none' to skip sending):");
                for (int i = 0; i < otherMembers.size(); i++) {
                    System.out.println((i + 1) + ". " + otherMembers.get(i));
                }
                String choices = scanner.nextLine().trim();

                if ("all".equals(choices)) {
                    recipients.addAll(otherMembers);
                } else if (!"none".equals(choices) && !choices.isEmpty()) {
                    try {
                        Arrays.stream(choices.split("\\s+"))
                                .map(Integer::parseInt)
                                .map(i -> i - 1)
                                .filter(i -> i >= 0 && i < otherMembers.size())
                                .forEach(i -> recipients.add(otherMembers.get(i)));
                    } catch (NumberFormatException e) {
                        System.err.println("Invalid input. Sending to no one.");
                    }
                }

                for (InetSocketAddress member : recipients) {
                    sendUnicast(msg, member);
                }
            }
        }
    }

    private void sendUnicast(StableMulticastMessage msg, InetSocketAddress destination) {
        threadPool.execute(() -> {
            try {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(bos);
                oos.writeObject(msg);
                byte[] data = bos.toByteArray();

                DatagramPacket packet = new DatagramPacket(data, data.length, destination.getAddress(),
                        destination.getPort());
                unicastSocket.send(packet);
                System.out.println("Sent unicast message to " + destination + ": " + msg.getContent());
            } catch (IOException e) {
                System.err.println("Error sending unicast message to " + destination + ": " + e.getMessage());
            }
        });
    }

    private void displayClockAndBuffer() {
        System.out.println("\n--- Current State (P" + myId + ") ---");
        synchronized (clockLock) {
            System.out.println(mc);
        }
        synchronized (bufferLock) {
            System.out.println("Message Buffer (" + messageBuffer.size() + " messages):");
            if (messageBuffer.isEmpty()) {
                System.out.println("  [Empty]");
            } else {
                for (StableMulticastMessage m : messageBuffer) {
                    System.out.println("  - " + m);
                }
            }
        }
        System.out.println("--------------------------\n");
    }

    public void shutdown() {
        running = false;
        if (scanner != null) {
            scanner.close();
        }
        if (threadPool != null) {
            threadPool.shutdownNow();
        }
        if (scheduledThreadPool != null) {
            scheduledThreadPool.shutdownNow();
        }
        if (unicastSocket != null) {
            unicastSocket.close();
        }
        if (multicastSocket != null) {
            try {
                multicastSocket.leaveGroup(InetAddress.getByName(MULTICAST_ADDRESS));
            } catch (IOException e) {
                System.err.println("Error leaving multicast group: " + e.getMessage());
            }
            multicastSocket.close();
        }
        System.out.println("StableMulticast shutdown complete.");
    }
}