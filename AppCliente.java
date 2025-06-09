import StableMulticast.*;

import java.io.IOException;
import java.util.Scanner;

public class AppCliente implements IStableMulticast {
    private StableMulticast middleware;

    public AppCliente(String ip, int port) throws IOException {
        this.middleware = new StableMulticast(ip, port, this);
    }

    @Override
    public void deliver(String msg) {
        System.out.println("[Recebido] " + msg);
    }

    public void iniciarEnvio() {
        Scanner sc = new Scanner(System.in);
        while (true) {
            System.out.print("Digite uma mensagem para enviar (ou 'sair'): ");
            String mensagem = sc.nextLine();
            if (mensagem.equalsIgnoreCase("sair")) break;
            try {
                middleware.msend(mensagem, this);
            } catch (IOException e) {
                System.out.println("Erro ao enviar: " + e.getMessage());
            }
        }
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.out.println("Uso: java AppCliente <ip_multicast> <porta>");
            return;
        }

        AppCliente cliente = new AppCliente(args[0], Integer.parseInt(args[1]));
        cliente.iniciarEnvio();
    }
}
