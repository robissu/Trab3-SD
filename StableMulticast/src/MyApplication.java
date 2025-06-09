import StableMulticast.IStableMulticast;
import StableMulticast.StableMulticast;

import java.io.IOException;
import java.util.Scanner;

public class MyApplication implements IStableMulticast {

    private StableMulticast stableMulticast;
    private String myName;

    public MyApplication(String name, String ip, int port) {
        this.myName = name;
        try {
            this.stableMulticast = new StableMulticast(ip, port, this);
        } catch (IOException e) {
            System.err.println("Error initializing StableMulticast: " + e.getMessage());
            System.exit(1);
        }
    }

    @Override
    public void deliver(String msg) {
        System.out.println("[" + myName + "] DELIVERED: " + msg);
    }

    public void run() {
        Scanner scanner = new Scanner(System.in);
        System.out.println("\n[" + myName + "] Ready. Enter messages to send, or 'exit' to quit.");

        while (true) {
            System.out.print("[" + myName + "] Enter message: ");
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
            System.out.println("Usage: java MyApplication <name> <ip> <port>");
            System.out.println("Example: java MyApplication P1 127.0.0.1 5000");
            return;
        }

        String name = args[0];
        String ip = args[1];
        int port = Integer.parseInt(args[2]);

        MyApplication app = new MyApplication(name, ip, port);
        app.run();
    }
}