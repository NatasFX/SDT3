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
        System.out.println("Mensagem recebida: " + msg);
    }

    public void enviarMensagem(String msg) {
        middleware.mcsend(msg, this);
    }
    
    public static void main(String args[]) {
        // esse ip é bem aleatorio, não sei se tem algum requisito
        Client clt = new Client("228.5.6.7", 9000);
        while (true) {
            System.out.print("msg: ");
            Scanner scanf = new Scanner(System.in);
            String m = scanf.nextLine();
            System.out.println("Sending...");
            clt.enviarMensagem(m);
            // try {
            //     Thread.sleep(5000);
            // } catch (InterruptedException e) {
            //     e.printStackTrace();
            // }
        }
        // falta fazer um jeito de dar input pelo terminal pra msg
    }
}