import java.util.Scanner;

import CausalMulticast.*;

//ABC: não faço ideia de como os clientes vão funcionar
public class Client implements ICausalMulticast {
    private CausalMulticast middleware;

    public Client(String middlewareIp, int middlewarePort) {
        middleware = new CausalMulticast(middlewareIp, middlewarePort, this);
    }

    @Override
    public void deliver(String msg) {
        System.out.println("\rMensagem recebida: " + msg);
    }

    public void enviarMensagem(String msg) {
        middleware.mcsend(msg, this);
    }
    
    public static void main(String args[]) {
        // esse ip é bem aleatorio, não sei se tem algum requisito
        Client clt = new Client("228.0.0.1", 9000);
        while (true) {
            System.out.print("msg: ");
            Scanner scanf = new Scanner(System.in);
            String m = scanf.nextLine();
            System.out.println("Sending...");
            clt.enviarMensagem(m);
        }
    }
}