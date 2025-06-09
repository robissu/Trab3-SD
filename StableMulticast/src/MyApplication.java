import StableMulticast.IStableMulticast;
import StableMulticast.StableMulticast;

import java.io.IOException;
import java.util.Scanner;

public class MyApplication implements IStableMulticast {

    private StableMulticast stableMulticast;
    private String myNamePrefix; // Renomeado para ser um prefixo, não o nome final
    private int assignedId = -1; // Para armazenar o ID real atribuído pelo middleware

    public MyApplication(String namePrefix, String ip, int port) {
        this.myNamePrefix = namePrefix;
        try {
            this.stableMulticast = new StableMulticast(ip, port, this);
            // O ID será atribuído dinamicamente, então não podemos pegá-lo aqui imediatamente
            // Ele será atualizado quando o serviço de descoberta for executado pela primeira vez.
        } catch (IOException e) {
            System.err.println("Error initializing StableMulticast: " + e.getMessage());
            System.exit(1);
        }
    }

    @Override
    public void deliver(String msg) {
        // Obtenha o ID atual do middleware antes de exibir a mensagem
        int currentId = stableMulticast.getId();
        if (currentId != -1 && currentId != assignedId) {
            assignedId = currentId; // Atualiza o ID atribuído se mudou
        }
        System.out.println("[" + myNamePrefix + assignedId + "] DELIVERED: " + msg);
    }

    public void run() {
        Scanner scanner = new Scanner(System.in);
        // Tenta obter o ID logo no início da execução do run()
        // O discovery service já deve ter rodado pelo menos uma vez ou vai rodar em breve.
        int initialId = stableMulticast.getId();
        if (initialId != -1) {
            assignedId = initialId;
        }

        System.out.println("\n[" + myNamePrefix + assignedId + "] Ready. Enter messages to send, or 'exit' to quit.");

        while (true) {
            // Verifica e atualiza o ID a cada loop para capturar mudanças dinâmicas
            int currentId = stableMulticast.getId();
            if (currentId != -1 && currentId != assignedId) {
                assignedId = currentId;
                System.out.println("--- [" + myNamePrefix + assignedId + "] ID updated! ---");
            }

            System.out.print("[" + myNamePrefix + assignedId + "] Enter message: ");
            String input = scanner.nextLine();

            if ("exit".equalsIgnoreCase(input)) {
                break;
            }
            if (!input.trim().isEmpty()) {
                stableMulticast.msend(input, this);
            }
        }

        scanner.close();
        stableMulticast.shutdown();
    }

    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("Usage: java MyApplication <namePrefix> <ip> <port>");
            System.out.println("Example: java MyApplication P 127.0.0.1 5000"); // Note o 'P' em vez de 'P1'
            return;
        }

        String namePrefix = args[0]; // Agora é um prefixo, por exemplo, "P"
        String ip = args[1];
        int port = Integer.parseInt(args[2]);

        MyApplication app = new MyApplication(namePrefix, ip, port);
        app.run();
    }
}