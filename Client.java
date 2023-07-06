import java.util.Scanner;

import CausalMulticast.*;

/**
 * Nossa classe cliente
*/
public class Client implements ICausalMulticast {
    private CausalMulticast middleware;
    private static Scanner scanf = new Scanner(System.in);

    /**
     * Construtor do Cliente
     */
    public Client(String middlewareIp, int middlewarePort) {
        middleware = new CausalMulticast(middlewareIp, middlewarePort, this);
    }

    /**
     * Método deliver
     */
    @Override
    public void deliver(String msg) {
        System.out.println("\r[CLIENTE] Mensagem recebida: " + msg);
    }
    /**
     * Método para envio de mensagem
     */
    public void enviarMensagem(String msg) {
        middleware.mcsend(msg, this);
    }
    
    /*
     * Main
     */
    public static void main(String args[]) {
        // esse ip é bem aleatorio, não sei se tem algum requisito
        Client clt = new Client("228.0.0.1", 9000);
        while (true) {
            System.out.print("msg: ");
            String m = scanf.nextLine();
            clt.enviarMensagem(m);
        }
    }
}