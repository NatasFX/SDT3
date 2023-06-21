import CausalMulticast.*;

//ABC: não faço ideia de como os clientes vão funcionar
public class Client implements ICausalMulticast {
    private CausalMulticast middleware;
    private static final String GROUP_IP = "239.0.0.1";
    private static final int GROUP_PORT = 1234;

    public Client(String middlewareIp, int middlewarePort) {
        middleware = new CausalMulticast(middlewareIp, middlewarePort, this,GROUP_IP, GROUP_PORT);
    }

    // Implementação do método deliver da interface ICausalMulticast
    @Override
    public void deliver(String msg) {
        // Tratar a mensagem multicast recebida pelo middleware
        System.out.println("Mensagem recebida: " + msg);
    }

    // Método para enviar mensagem multicast
    public void enviarMensagem(String msg) {
        middleware.mcsend(msg, this);
    }
}
