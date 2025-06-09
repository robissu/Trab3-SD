package StableMulticast;

import java.io.*;
import java.net.*;
import java.util.*;

public class StableMulticast {
    private final String multicastIP;
    private final int listenPort;
    private final IStableMulticast client;
    private final DatagramSocket socket;
    private final Set<InetSocketAddress> groupMembers = new HashSet<>();
    private final List<Message> buffer = new ArrayList<>();
    private final int id;
    private int[][] MCi;
    private final int maxMembers = 10;
    private final int DISCOVERY_PORT = 6000;
    private final String DISCOVERY_MSG = "DISCOVERY";

    public StableMulticast(String ip, Integer listenPort, IStableMulticast client) throws IOException {
        this.multicastIP = ip;
        this.listenPort = listenPort;
        this.client = client;
        this.socket = new DatagramSocket(listenPort);
        this.id = new Random().nextInt(maxMembers); // Garante que id ∈ [0, maxMembers-1]
        this.MCi = new int[maxMembers][maxMembers];

        discoverMembers();
        listen();
        startStabilizationThread();
    }

    private void discoverMembers() throws IOException {
        MulticastSocket discoverySocket = new MulticastSocket(DISCOVERY_PORT);
        InetAddress group = InetAddress.getByName(multicastIP);
        discoverySocket.joinGroup(group);

        String msg = DISCOVERY_MSG + ";" + id + ";" + InetAddress.getLocalHost().getHostAddress() + ";" + listenPort;
        byte[] data = msg.getBytes();
        DatagramPacket packet = new DatagramPacket(data, data.length, group, DISCOVERY_PORT);
        discoverySocket.send(packet);

        new Thread(() -> {
            while (true) {
                try {
                    byte[] buf = new byte[1024];
                    DatagramPacket p = new DatagramPacket(buf, buf.length);
                    discoverySocket.receive(p);
                    String m = new String(p.getData(), 0, p.getLength());
                    if (m.startsWith(DISCOVERY_MSG)) {
                        String[] parts = m.split(";");
                        int peerId = Integer.parseInt(parts[1]);
                        if (peerId != id) {
                            InetSocketAddress member = new InetSocketAddress(parts[2], Integer.parseInt(parts[3]));
                            groupMembers.add(member);
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public void msend(String msg, IStableMulticast sender) throws IOException {
        int[] vector = MCi[id];
        Message message = new Message(id, Arrays.copyOf(vector, vector.length), msg);

        Scanner scanner = new Scanner(System.in);
        for (InetSocketAddress member : groupMembers) {
            System.out.println("Enviar para " + member + "? (s/n)");
            if (scanner.nextLine().trim().equalsIgnoreCase("s")) {
                sendUnicast(message, member);
            }
        }

        MCi[id][id]++;
    }

    private void sendUnicast(Message message, InetSocketAddress target) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(message);
        byte[] data = baos.toByteArray();

        DatagramPacket packet = new DatagramPacket(data, data.length, target);
        socket.send(packet);
    }

    private void listen() {
        new Thread(() -> {
            while (true) {
                try {
                    byte[] buffer = new byte[4096];
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);

                    ByteArrayInputStream bais = new ByteArrayInputStream(packet.getData(), 0, packet.getLength());
                    ObjectInputStream ois = new ObjectInputStream(bais);
                    Message msg = (Message) ois.readObject();

                    handleMessage(msg);
                } catch (IOException | ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void handleMessage(Message msg) {
        buffer.add(msg);
        MCi[msg.sender] = msg.vectorClock;
        if (msg.sender != id) MCi[id][msg.sender]++;

        client.deliver(msg.content);
    }

    private void startStabilizationThread() {
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(2000);
                    stabilizeBuffer();
                    printStatus();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void stabilizeBuffer() {
        buffer.removeIf(msg -> {
            int min = Integer.MAX_VALUE;
            for (int[] mci : MCi) {
                min = Math.min(min, mci[msg.sender]);
            }
            return msg.vectorClock[msg.sender] <= min;
        });
    }

    private void printStatus() {
        System.out.println("--- MCi ---");
        for (int i = 0; i < maxMembers; i++) {
            System.out.println(Arrays.toString(MCi[i]));
        }
        System.out.println("Buffer size: " + buffer.size());
    }
}
