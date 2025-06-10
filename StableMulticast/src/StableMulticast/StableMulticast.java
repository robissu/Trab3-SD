package StableMulticast;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class StableMulticast {

    private static final String MULTICAST_ADDRESS_STR = "230.0.0.1";
    private static final int MULTICAST_PORT = 4446;
    private static final int DISCOVERY_INTERVAL_MS = 2000;

    private int myId;
    private String myIp;
    private Integer myPort;
    private IStableMulticast clientCallback;

    private volatile MulticastClock mc;
    private volatile List<StableMulticastMessage> messageBuffer;
    private final List<InetSocketAddress> groupMembers;

    private DatagramSocket unicastSocket;
    private MulticastSocket multicastSocket;
    private InetAddress MULTICAST_ADDRESS;

    private ExecutorService threadPool;
    private ScheduledExecutorService scheduledThreadPool;

    private volatile boolean running;
    private final Object clockLock = new Object();
    private final Object bufferLock = new Object();

    private Scanner scanner;

    public StableMulticast(String ip, Integer port, IStableMulticast client) throws IOException {
        this.myIp = ip;
        this.myPort = port;
        this.clientCallback = client;
        this.myId = -1;

        this.messageBuffer = new LinkedList<>();
        this.groupMembers = Collections.synchronizedList(new ArrayList<>());
        this.mc = new MulticastClock(1);
        this.MULTICAST_ADDRESS = InetAddress.getByName(MULTICAST_ADDRESS_STR);

        try {
            this.unicastSocket = new DatagramSocket(myPort, InetAddress.getByName(myIp));
            // System.out.println("StableMulticast: Socket unicast ligado a " + myIp + ":" + myPort); // Removido log de inicialização de socket unicast

            this.multicastSocket = new MulticastSocket(MULTICAST_PORT);
            // System.out.println("StableMulticast: Socket multicast criado na porta " + MULTICAST_PORT); // Removido log de inicialização de socket multicast

            InetAddress localInterface = InetAddress.getByName(myIp);
            this.multicastSocket.setInterface(localInterface);
            // System.out.println("StableMulticast: Interface do socket multicast definida para " + localInterface.getHostAddress()); // Removido log de interface definida

            this.multicastSocket.joinGroup(MULTICAST_ADDRESS);
            this.multicastSocket.setTimeToLive(1);
            System.out.println("StableMulticast: Entrou no grupo multicast " + MULTICAST_ADDRESS.getHostAddress() + " com TTL=1."); // Mantido, é um log importante de configuração

        } catch (IOException e) {
            System.err.println("StableMulticast: ERRO FATAL durante a inicialização do socket: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }

        this.threadPool = Executors.newCachedThreadPool();
        this.scheduledThreadPool = Executors.newScheduledThreadPool(2);
        this.running = true;
        this.scanner = new Scanner(System.in);

        startDiscoveryService();
        startUnicastReceiver();
        startStabilizationChecker();

        System.out.println("StableMulticast inicializado. Meu IP: " + myIp + ", Porta: " + myPort);
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
                        MULTICAST_ADDRESS, MULTICAST_PORT);
                multicastSocket.send(packet);
                // System.out.println("DiscoveryService: ENVIANDO mensagem de presença de: " + myIp + ":" + myPort + " para o grupo multicast " + MULTICAST_ADDRESS.getHostAddress() + ":" + MULTICAST_PORT); // Removido log de envio constante

                // Listen for presence messages
                byte[] receiveBuf = new byte[256];
                DatagramPacket receivePacket = new DatagramPacket(receiveBuf, receiveBuf.length);
                multicastSocket.setSoTimeout(DISCOVERY_INTERVAL_MS / 2);

                Set<InetSocketAddress> currentMembers = new HashSet<>();
                currentMembers.add(new InetSocketAddress(myIp, myPort));

                while (true) {
                    try {
                        multicastSocket.receive(receivePacket);
                        String received = new String(receivePacket.getData(), 0, receivePacket.getLength());
                        // String senderIp = receivePacket.getAddress().getHostAddress(); // Removido para simplificar log abaixo
                        // int senderPort = receivePacket.getPort(); // Removido para simplificar log abaixo
                        // System.out.println("DiscoveryService: RECEBIDO mensagem de presença de: " + senderIp + ":" + senderPort + " -> Dados: " + received); // Removido log de recebimento constante


                        String[] parts = received.split(":");
                        if (parts.length == 2) {
                            String memberIp = parts[0];
                            int memberPort = Integer.parseInt(parts[1]);
                            InetSocketAddress memberAddress = new InetSocketAddress(memberIp, memberPort);

                            if (!memberAddress.equals(new InetSocketAddress(myIp, myPort))) {
                                currentMembers.add(memberAddress);
                            }
                        }
                    } catch (SocketTimeoutException ste) {
                        break;
                    } catch (IOException e) {
                        System.err.println("DiscoveryService: ERRO durante a recepção/processamento multicast: " + e.getMessage());
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

                        this.myId = groupMembers.indexOf(new InetSocketAddress(myIp, myPort));
                        if (this.myId == -1) {
                            System.err.println("Erro: Meu próprio endereço não encontrado nos membros do grupo ordenados!");
                            return;
                        }

                        synchronized (clockLock) {
                            if (mc.getMc().length != groupMembers.size()) {
                                System.out.println("Resizing MulticastClock de " + mc.getMc().length + " para "
                                        + groupMembers.size());
                                MulticastClock newMc = new MulticastClock(groupMembers.size());
                                for (int i = 0; i < Math.min(mc.getMc().length, newMc.getMc().length); i++) {
                                    for (int j = 0; j < Math.min(mc.getMc()[i].length, newMc.getMc()[i].length); j++) {
                                        newMc.getMc()[i][j] = mc.getMc()[i][j];
                                    }
                                }
                                mc = newMc;
                            }
                        }
                        // ESTE LOG É IMPORTANTE E FOI MANTIDO
                        System.out.println("Membros do grupo atualizados. Meu ID: P" + myId + ", Membros: "
                                + groupMembers.stream().map(m -> m.getAddress().getHostAddress() + ":" + m.getPort()).collect(Collectors.joining(", ")));
                        displayClockAndBuffer();
                    }
                }

            } catch (IOException e) {
                System.err.println("Erro no serviço de descoberta: " + e.getMessage());
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
                    if (running) {
                        System.err.println("Socket unicast fechado inesperadamente: " + se.getMessage());
                    }
                } catch (IOException | ClassNotFoundException e) {
                    System.err.println("Erro ao receber mensagem unicast: " + e.getMessage());
                }
            }
        });
    }

    private void processReceivedMessage(StableMulticastMessage msg) {
        synchronized (bufferLock) {
            messageBuffer.add(msg);
        }

        // System.out.println("Recebido: " + msg); // Mantido o log de "Recebido" para feedback imediato

        synchronized (clockLock) {
            if (msg.getSenderId() >= 0 && msg.getSenderId() < mc.getMc().length) {
                mc.updateVector(msg.getSenderId(), msg.getSenderVC());

                if (myId != -1 && myId != msg.getSenderId() && myId < mc.getMc().length) {
                    mc.increment(myId, msg.getSenderId());
                }
            } else {
                System.err.println("Aviso: Mensagem recebida de um ID de remetente desconhecido (" + msg.getSenderId()
                        + "). Descartando atualização do clock.");
            }
        }

        clientCallback.deliver(msg.getContent());
        displayClockAndBuffer();
    }

    private void startStabilizationChecker() {
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
                        System.err.println("Aviso: Mensagem no buffer tem ID de remetente inválido (" + sender
                                + "). Pulando verificação de estabilização.");
                        continue;
                    }

                    boolean isStable = true;
                    int minClockValueForSender = Integer.MAX_VALUE;

                    for (int x = 0; x < mc.getMc().length; x++) {
                        if (x < mc.getMc().length && sender < mc.getMc()[x].length) {
                            minClockValueForSender = Math.min(minClockValueForSender, mc.getMc()[x][sender]);
                        } else {
                            isStable = false;
                            break;
                        }
                    }

                    if (isStable) {
                        if (msg.getSenderVC()[sender] <= minClockValueForSender) {
                            System.out.println("Descartando mensagem estável: " + msg.getContent() + " de P" + sender +
                                    " (VC[" + sender + "] = " + msg.getSenderVC()[sender] +
                                    " <= min(MC[x][" + sender + "]) = " + minClockValueForSender + ")");
                            iterator.remove();
                            displayClockAndBuffer();
                        }
                    }
                }
            }
        }
    }

    public void msend(String msgContent, IStableMulticast client) {
        if (myId == -1) {
            System.err.println("Não é possível enviar mensagem: Meu ID ainda não foi atribuído. Aguardando descoberta...");
            return;
        }
        if (groupMembers.size() <= 1) {
            System.err.println("Não é possível enviar mensagem: Nenhum outro membro no grupo ainda.");
            return;
        }

        synchronized (clockLock) {
            int[] senderVC = mc.getVector(myId);
            StableMulticastMessage msg = new StableMulticastMessage(msgContent, senderVC, myId);

            mc.increment(myId, myId);
            displayClockAndBuffer();

            System.out.println("\nPreparando para enviar mensagem: \"" + msgContent + "\"");
            System.out.print("Enviar para TODOS os membros? (sim/nao): ");
            String response = scanner.nextLine().trim().toLowerCase();

            if ("sim".equals(response)) {
                for (InetSocketAddress member : groupMembers) {
                    if (!member.equals(new InetSocketAddress(myIp, myPort))) {
                        sendUnicast(msg, member);
                    }
                }
            } else {
                List<InetSocketAddress> recipients = new ArrayList<>();
                List<InetSocketAddress> otherMembers = groupMembers.stream()
                        .filter(m -> !m.equals(new InetSocketAddress(myIp, myPort)))
                        .collect(Collectors.toList());

                System.out.println(
                        "Escolha os destinatários (digite números separados por espaço, ou 'todos' para todos, 'nenhum' para pular o envio):");
                for (int i = 0; i < otherMembers.size(); i++) {
                    System.out.println((i + 1) + ". " + otherMembers.get(i));
                }
                String choices = scanner.nextLine().trim();

                if ("todos".equals(choices)) {
                    recipients.addAll(otherMembers);
                } else if (!"nenhum".equals(choices) && !choices.isEmpty()) {
                    try {
                        Arrays.stream(choices.split("\\s+"))
                                .map(Integer::parseInt)
                                .map(i -> i - 1)
                                .filter(i -> i >= 0 && i < otherMembers.size())
                                .forEach(i -> recipients.add(otherMembers.get(i)));
                    } catch (NumberFormatException e) {
                        System.err.println("Entrada inválida. Não enviando para ninguém.");
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
                System.out.println("Mensagem unicast enviada para " + destination + ": \"" + msg.getContent() + "\"");
            } catch (IOException e) {
                System.err.println("Erro ao enviar mensagem unicast para " + destination + ": " + e.getMessage());
            }
        });
    }

    private void displayClockAndBuffer() {
        System.out.println("\n--- Estado Atual (P" + myId + ") ---");
        synchronized (clockLock) {
            System.out.println(mc);
        }
        synchronized (bufferLock) {
            System.out.println("Buffer de Mensagens (" + messageBuffer.size() + " mensagens):");
            if (messageBuffer.isEmpty()) {
                System.out.println("  [Vazio]");
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
                // MULTICAST_ADDRESS já é um InetAddress, não precisa de getByName novamente
                multicastSocket.leaveGroup(MULTICAST_ADDRESS);
            } catch (IOException e) {
                System.err.println("Erro ao sair do grupo multicast: " + e.getMessage());
            }
            multicastSocket.close();
        }
        System.out.println("StableMulticast desligado completamente.");
    }
}