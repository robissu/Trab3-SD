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
    // private static final int DISPLAY_INTERVAL_MS = 2000; // Display state every 2 seconds - REMOVIDO!

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

    private Scanner scanner; // Para controle de input do usuário no msend

    public StableMulticast(String ip, Integer port, IStableMulticast client) throws IOException {
        this.myIp = ip;
        this.myPort = port;
        this.clientCallback = client;
        this.myId = -1; // Will be assigned dynamically

        this.messageBuffer = new ArrayList<>();
        this.groupMembers = Collections.synchronizedList(new ArrayList<>()); // Thread-safe list

        this.unicastSocket = new DatagramSocket(myPort, InetAddress.getByName(myIp));

        this.multicastSocket = new MulticastSocket(MULTICAST_PORT);
        InetAddress localInterface = InetAddress.getByName(myIp);
        this.multicastSocket.setInterface(localInterface);
        this.multicastSocket.joinGroup(InetAddress.getByName(MULTICAST_ADDRESS));
        this.multicastSocket.setTimeToLive(1); // Limit multicast to local subnet

        this.threadPool = Executors.newCachedThreadPool();
        this.scheduledThreadPool = Executors.newScheduledThreadPool(2);

        this.running = true;
        this.scanner = new Scanner(System.in);

        startDiscoveryService();
        startMulticastDiscoveryReceiver();
        startUnicastReceiver();
        startStabilizationChecker();
        // startDisplayService(); // <--- REMOVIDO: Não mais exibição periódica automática

        // Nova chamada para exibir o estado inicial após a inicialização
        displayClockAndBuffer();
    }

    public int getId() {
        return myId;
    }

    private void startDiscoveryService() {
        scheduledThreadPool.scheduleAtFixedRate(() -> {
            if (!running) return;
            try {
                InetAddress multicastGroup = InetAddress.getByName(MULTICAST_ADDRESS);

                String discoveryMsg = myIp + ":" + myPort;
                byte[] data = discoveryMsg.getBytes();
                DatagramPacket packet = new DatagramPacket(data, data.length, multicastGroup, MULTICAST_PORT);
                multicastSocket.send(packet);

                List<InetSocketAddress> sortedMembers;
                synchronized (groupMembers) {
                    if (!groupMembers.contains(new InetSocketAddress(myIp, myPort))) {
                        groupMembers.add(new InetSocketAddress(myIp, myPort));
                    }
                    sortedMembers = groupMembers.stream()
                            .sorted(Comparator
                                    .comparing((InetSocketAddress addr) -> addr.getAddress().getHostAddress())
                                    .thenComparing(InetSocketAddress::getPort))
                            .collect(Collectors.toList());
                }

                int newId = sortedMembers.indexOf(new InetSocketAddress(myIp, myPort));

                synchronized (clockLock) {
                    if (mc == null || newId != myId || sortedMembers.size() != mc.getNumberOfProcesses()) {
                        System.out.println("\n[P" + myId + "] Group membership potentially changed. Re-assigning IDs. My old ID: P" + myId + " -> New ID: P" + newId + ".");
                        MulticastClock newMc = new MulticastClock(sortedMembers.size());
                        if (mc != null) {
                            int oldSize = mc.getNumberOfProcesses();
                            for (int i = 0; i < Math.min(oldSize, newMc.getNumberOfProcesses()); i++) {
                                for (int j = 0; j < Math.min(oldSize, newMc.getNumberOfProcesses()); j++) {
                                    if (i < newMc.getNumberOfProcesses() && j < newMc.getNumberOfProcesses()) {
                                        newMc.getMc()[i][j] = mc.getMc()[i][j];
                                    }
                                }
                            }
                        }
                        mc = newMc;
                        myId = newId;
                        // displayClockAndBuffer(); // Pode ser chamado aqui se quiser que a descoberta inicial force uma exibição.
                                                 // Mas se for muito frequente, ainda pode ser disruptivo. Mantendo fora por enquanto.
                    }
                }
            } catch (IOException e) {
                System.err.println("Error during discovery service: " + e.getMessage());
            }
        }, 0, DISCOVERY_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private void startMulticastDiscoveryReceiver() {
        threadPool.submit(() -> {
            byte[] buffer = new byte[256];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            while (running) {
                try {
                    multicastSocket.receive(packet);
                    String receivedData = new String(packet.getData(), packet.getOffset(), packet.getLength());
                    
                    if (receivedData.matches("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d+")) {
                        String[] parts = receivedData.split(":");
                        String discoveredIp = parts[0];
                        int discoveredPort = Integer.parseInt(parts[1]);
                        InetSocketAddress discoveredMember = new InetSocketAddress(discoveredIp, discoveredPort);

                        if (!discoveredMember.equals(new InetSocketAddress(myIp, myPort)) && !groupMembers.contains(discoveredMember)) {
                            synchronized (groupMembers) {
                                groupMembers.add(discoveredMember);
                                System.out.println("[P" + myId + "] Discovered new member: " + discoveredMember.getHostString() + ":" + discoveredMember.getPort());
                                // Não chamamos displayClockAndBuffer aqui para evitar interferir com prompts
                                // A atualização da lista de membros e ID será refletida na próxima exibição manual.
                            }
                        }
                    }
                } catch (SocketException e) {
                    if (running) {
                        System.err.println("Multicast discovery receiver socket error: " + e.getMessage());
                    }
                } catch (IOException | NumberFormatException e) {
                    System.err.println("Error receiving multicast discovery: " + e.getMessage());
                }
            }
        });
    }


    private void startUnicastReceiver() {
        threadPool.submit(() -> {
            byte[] buffer = new byte[65535];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            while (running) {
                try {
                    unicastSocket.receive(packet);
                    ByteArrayInputStream bis = new ByteArrayInputStream(packet.getData(), packet.getOffset(), packet.getLength());
                    ObjectInputStream ois = new ObjectInputStream(bis);
                    StableMulticastMessage receivedMsg = (StableMulticastMessage) ois.readObject();

                    InetSocketAddress senderAddress = new InetSocketAddress(packet.getAddress(), packet.getPort());
                    if (!groupMembers.contains(senderAddress)) {
                        synchronized (groupMembers) {
                            groupMembers.add(senderAddress);
                        }
                    }
                    processReceivedMessage(receivedMsg);
                    // CHAMA A EXIBIÇÃO APÓS RECEBER E PROCESSAR UMA MENSAGEM
                    displayClockAndBuffer(); 

                } catch (SocketException e) {
                    if (running) {
                        System.err.println("Unicast receiver socket error: " + e.getMessage());
                    }
                } catch (IOException | ClassNotFoundException e) {
                    System.err.println("Error receiving unicast: " + e.getMessage());
                }
            }
        });
    }

    private void processReceivedMessage(StableMulticastMessage msg) {
        synchronized (bufferLock) {
            messageBuffer.add(msg);
        }

        synchronized (clockLock) {
            int requiredSize = Math.max(myId, msg.getSenderId()) + 1;
            if (mc.getNumberOfProcesses() < requiredSize) {
                MulticastClock tempMc = new MulticastClock(requiredSize);
                int oldSize = mc.getNumberOfProcesses();
                for (int i = 0; i < oldSize; i++) {
                    System.arraycopy(mc.getMc()[i], 0, tempMc.getMc()[i], 0, oldSize);
                }
                mc = tempMc;
            }

            mc.updateVector(msg.getSenderId(), msg.getSenderVC());
            mc.increment(myId, msg.getSenderId());
        }

        clientCallback.deliver(msg.getContent());
        //System.out.println("entreguei a msg:" + msg.getContent());
    }

    private void startStabilizationChecker() {
        scheduledThreadPool.scheduleAtFixedRate(() -> {
            if (!running) return;
            try {
                int oldBufferSize;
                synchronized (bufferLock) {
                    oldBufferSize = messageBuffer.size();
                }
                checkAndDiscardStableMessages();
                int newBufferSize;
                synchronized (bufferLock) {
                    newBufferSize = messageBuffer.size();
                }
                // CHAMA A EXIBIÇÃO APENAS SE HOUVE MUDANÇA (mensagens descartadas)
                if (newBufferSize < oldBufferSize) {
                    displayClockAndBuffer();
                }
            } catch (Exception e) {
                System.err.println("Error in stabilization checker: " + e.getMessage());
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
    }

    private void checkAndDiscardStableMessages() {
        List<StableMulticastMessage> stableToDiscard = new ArrayList<>();
        synchronized (bufferLock) {
            Iterator<StableMulticastMessage> iterator = messageBuffer.iterator();
            while (iterator.hasNext()) {
                StableMulticastMessage msg = iterator.next();
                int sender = msg.getSenderId();

                if (mc.getNumberOfProcesses() <= sender || sender == -1) {
                    continue;
                }

                boolean isStable = true;
                synchronized (clockLock) {
                    int senderVC_for_sender = msg.getSenderVC()[sender];
                    int minClockValueForSender = Integer.MAX_VALUE;

                    for (int i = 0; i < mc.getNumberOfProcesses(); i++) {
                        if (mc.getValue(i, sender) < minClockValueForSender) {
                            minClockValueForSender = mc.getValue(i, sender);
                        }
                    }

                    if (senderVC_for_sender > minClockValueForSender) {
                        isStable = false;
                    }
                }

                if (isStable) {
                    stableToDiscard.add(msg);
                }
            }
            messageBuffer.removeAll(stableToDiscard);
        }
    }

    private void sendUnicast(StableMulticastMessage msg, InetSocketAddress destination) {
        threadPool.submit(() -> {
            try {
                int delayMillis = 1000;
                // Log de envio, mas sem o "Simulando atraso" separado para reduzir ruído
                System.out.println("[P" + myId + "] Sending unicast: '" + msg.getContent() + "' to " + destination.getHostString() + ":" + destination.getPort() + " (delay: " + delayMillis + "ms)");
                Thread.sleep(delayMillis);

                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(bos);
                oos.writeObject(msg);
                oos.flush();
                byte[] data = bos.toByteArray();

                DatagramPacket packet = new DatagramPacket(data, data.length, destination.getAddress(), destination.getPort());
                unicastSocket.send(packet);
                // O display será chamado após o msend para mostrar o estado atualizado.
            } catch (IOException e) {
                System.err.println("Error sending unicast: " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("Unicast send interrupted during delay: " + e.getMessage());
            }
        });
    }

    // private void startDisplayService() { // <--- REMOVIDO
    //     scheduledThreadPool.scheduleAtFixedRate(this::displayClockAndBuffer, 0, DISPLAY_INTERVAL_MS, TimeUnit.MILLISECONDS);
    // }

    private void displayClockAndBuffer() {
        // Limpar a tela (funciona em terminais que suportam ANSI, como PowerShell, Git Bash, Linux/macOS)
        System.out.print("\033[H\033[2J");
        System.out.flush();

        System.out.println("--- P" + myId + " (IP:" + myIp + ", Port:" + myPort + ") Current State --- [" + new Date() + "]");
        synchronized (clockLock) {
            if (mc != null) {
                System.out.println(mc);
            } else {
                System.out.println("MulticastClock: Not initialized yet.");
            }
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
        System.out.println("-----------------------------------------");
    }

    public void msend(String msgContent, IStableMulticast client) {
        if (myId == -1) {
            System.out.println("Cannot send message: My ID is not yet assigned. Please wait for discovery.");
            return;
        }
        
        List<InetSocketAddress> availableOtherMembers = groupMembers.stream()
                .filter(member -> !(member.getAddress().getHostAddress().equals(myIp) && member.getPort() == myPort))
                .collect(Collectors.toList());

        if (availableOtherMembers.isEmpty()) {
            System.out.println("Cannot send message: No other members discovered yet. Please wait.");
            return;
        }

        StableMulticastMessage msg;
        synchronized (clockLock) {
            mc.increment(myId, myId);
            int[] senderVC = mc.getVector(myId);
            msg = new StableMulticastMessage(msgContent, senderVC, myId);
        }

        System.out.println("\n--- Sending Message: '" + msgContent + "' ---");
        System.out.println("  1. Send to All available members (no further prompts)");
        System.out.println("  2. Select specific members (with per-message control)");
        System.out.print("Enter choice (1 or 2): ");
        String initialChoice = scanner.nextLine().trim();

        List<InetSocketAddress> selectedRecipients = new ArrayList<>(); // Renomeado para clareza
        if ("1".equals(initialChoice)) {
            selectedRecipients.addAll(availableOtherMembers);
            System.out.println("\nSending message '" + msgContent + "' to all available members without further prompts.");
            for (InetSocketAddress member : selectedRecipients) { // Iterar sobre os membros selecionados
                sendUnicast(msg, member);
            }
            // Chama a exibição do estado atualizado após todos os envios no modo "Send to All"
            displayClockAndBuffer(); 

        } else if ("2".equals(initialChoice)) {
            System.out.println("Available members (excluding self):");
            for (int i = 0; i < availableOtherMembers.size(); i++) {
                System.out.println("  " + i + ". " + availableOtherMembers.get(i).getHostString() + ":" + availableOtherMembers.get(i).getPort());
            }
            System.out.print("Enter member indices (comma-separated, e.g., 0,1), or leave blank for all selectable: ");
            String indicesInput = scanner.nextLine().trim();

            if (indicesInput.isEmpty()) {
                selectedRecipients.addAll(availableOtherMembers);
            } else {
                String[] indices = indicesInput.split(",");
                for (String indexStr : indices) {
                    try {
                        int index = Integer.parseInt(indexStr.trim());
                        if (index >= 0 && index < availableOtherMembers.size()) {
                            selectedRecipients.add(availableOtherMembers.get(index));
                        } else {
                            System.err.println("Invalid index skipped: " + indexStr);
                        }
                    } catch (NumberFormatException e) {
                        System.err.println("Non-numeric index skipped: " + indexStr);
                    }
                }
            }

            if (selectedRecipients.isEmpty()) {
                System.out.println("No valid recipients selected. Message not sent.");
                return;
            }

            System.out.println("\nInitiating controlled unicast sends...");
            Iterator<InetSocketAddress> recipientIterator = selectedRecipients.iterator();
            while (recipientIterator.hasNext()) {
                InetSocketAddress member = recipientIterator.next();
                System.out.print("Send '" + msgContent + "' to " + member.getHostString() + ":" + member.getPort() + "? (y/n/s - 's' to skip remaining): ");
                String controlChoice = scanner.nextLine().trim().toLowerCase();

                if ("y".equals(controlChoice)) {
                    sendUnicast(msg, member);
                    System.out.println("  -> Sent to " + member.getHostString() + ":" + member.getPort());
                } else if ("n".equals(controlChoice)) {
                    System.out.println("  -> Skipped sending to " + member.getHostString() + ":" + member.getPort());
                } else if ("s".equals(controlChoice)) {
                    System.out.println("  -> Skipping remaining recipients.");
                    break;
                } else {
                    System.out.println("  -> Invalid choice. Skipping this recipient.");
                }
            }
            System.out.println("Controlled unicast sends complete for this message.\n");
            // Chama a exibição do estado atualizado após todos os envios no modo "Controlado"
            displayClockAndBuffer();

        } else {
            System.out.println("Invalid initial choice. Aborting send.");
            return;
        }
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