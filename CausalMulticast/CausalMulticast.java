package CausalMulticast;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public class CausalMulticast {

    private Map<String, Integer> vectorClock = new HashMap<>(); // Relógio vetorial
    private List<String> buffer = new ArrayList<>(); // Buffer de mensagens
    private List<String> members = new ArrayList<>(); // Membros do grupo

    private InetAddress group; //grupo multicast
    private int port; // porta
    private ICausalMulticast client; // Referência do usuário para callback
    private MulticastSocket socket; // socket para dar send
    private String name; // nome da maquina

    private Thread thread;

    private final int QNT_CLIENTES = 2;


    public CausalMulticast(String ip, Integer port, ICausalMulticast client) {

        System.setProperty("java.net.preferIPv4Stack", "true");

        // Inicialização do middleware
        this.client = client;
        this.port = port;

        Scanner scanf = new Scanner(System.in);
        print("Qual o nome da sua máquina?");
        this.name = scanf.nextLine();

        members.add(name);

        // criar grupo e entrar nele
        try {
            this.group = InetAddress.getByName(ip);
            this.socket = new MulticastSocket(port);
            socket.joinGroup(group);

            findOtherClients();
            // depois do while, todos os membros do multicast estão populados dentro de  `members`
        } catch (Exception e) {
            print("Erro ao criar/entrar grupo multicast " + e.toString());
        }
        

        this.thread = new Receiver(name, socket);

        // Inicia a execução da thread
        thread.start();
    }

    private void findOtherClients() throws Exception {
        print("Detectando outras máquinas...");

        byte[] buf = new byte[1000];

        while (members.size() < QNT_CLIENTES) {
            send(this.name);
            
            DatagramPacket recv = new DatagramPacket(buf, buf.length);

            socket.receive(recv);

            String data = new String(recv.getData(), 0, recv.getLength());

            if (data.equals(this.name)) {
                continue;
            } else {
                if (!members.contains(data))
                    members.add(data);
            }
        }
        print("Computadores conectados no grupo: " + '"' + String.join("\", \"", members) + '"');
    }

    // ninguem gosta de system.out.meudeus.quanto.negocio.eu.so.quero.printar
    private void print(String m) {
        System.out.println("[MIDDLEWARE] " + m);
    }

    private String encode(String destinatario, String msg) {
        return destinatario + ":" + msg;
    }

    

    private void send(String msg) {

        DatagramPacket packet = new DatagramPacket(msg.getBytes(), msg.length(), group, port);
        try {
            socket.send(packet);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void mcsend(String msg, ICausalMulticast client) {

        if (members.size() == 0) {
            print("Não há usuários para enviar esta mensagem.");
            return;
        }

        // Incrementa o relógio vetorial
        incrementVectorClock();

        // Adiciona a mensagem ao buffer
        buffer.add(msg);

        /* ABC Implementar:
         * Para possibilitar a correção do trabalho, faça o envio de cada mensagem unicast ser
         * controlado via teclado, ou seja, deve haver uma pergunta antes de cada envio unicast
         * (controle) questionando se é para enviar a todos ou não.
         * se for multicast, usar sendMulticastMessage(msg)
         */

        // Envia mensagem unicast para todos os membros do grupo
        for (String nome : members) {
            String m = encode(nome, msg);
            send(m);
        }
        print("Enviado para " + QNT_CLIENTES +" usuarios");
    }

    public void deliver(String msg, Map<String, Integer> senderClock) {
        // Atualiza o relógio vetorial com o relógio do remetente
        updateVectorClock(senderClock);

        // Adiciona a mensagem ao buffer
        buffer.add(msg);

        // Verifica se é possível entregar mensagens do buffer
        deliverMessagesFromBuffer();
    }

    private void incrementVectorClock() {
        // Incrementa o relógio vetorial do próprio processo
        String processId = getProcessId();
        int timestamp = vectorClock.getOrDefault(processId, 0);
        vectorClock.put(processId, timestamp + 1);
    }

    private void updateVectorClock(Map<String, Integer> senderClock) {
        // Atualiza o relógio vetorial com o relógio do remetente
        for (Map.Entry<String, Integer> entry : senderClock.entrySet()) {
            String processId = entry.getKey();
            int timestamp = entry.getValue();

            int currentTimestamp = vectorClock.getOrDefault(processId, 0);
            vectorClock.put(processId, Math.max(currentTimestamp, timestamp));
        }
    }

    private void deliverMessagesFromBuffer() {
        // Verifica se é possível entregar mensagens do buffer de acordo com o relógio vetorial
        List<String> messagesToDeliver = new ArrayList<>();

        for (String msg : buffer) {
            Map<String, Integer> msgClock = extractVectorClock(msg);
            boolean canDeliver = true;

            for (Map.Entry<String, Integer> entry : msgClock.entrySet()) {
                String processId = entry.getKey();
                int msgTimestamp = entry.getValue();
                int currentTimestamp = vectorClock.getOrDefault(processId, 0);

                if (msgTimestamp > currentTimestamp) {
                    canDeliver = false;
                    break;
                }
            }

            if (canDeliver) {
                messagesToDeliver.add(msg);
            }
        }

        // Remove mensagens do buffer e entrega ao cliente
        /* ABC: não ta da forma certa ainda, pra remover do buffer tem que implementar a parte do
        "algoritmo para estabilização das mensagens" que ta na especificação do trab */
        for (String msg : messagesToDeliver) {
            buffer.remove(msg);
            client.deliver(msg);
        }
    }

    /* ABC: essa parte é gpt, acho que precisamos perguntar pro professor como que as mensagens
     * vão enviar o Vector Clock no piggyback, não encontrei nas especificações.
     * No pseudocódigo é `msg.VC`, talvez tenhamos que criar uma classe/struct para as mensagens.
     */
    private Map<String, Integer> extractVectorClock(String msg) {
        // Extrai o relógio vetorial de uma mensagem
        // A implementação depende do formato ou estrutura utilizada para representar o relógio na mensagem
        // Neste exemplo, vamos supor que o relógio vetorial seja representado como uma string no formato "A:1,B:2,C:0"
        Map<String, Integer> clock = new HashMap<>();
        
        String[] clockParts = msg.split(",");
        for (String clockPart : clockParts) {
            String[] keyValue = clockPart.split(":");
            String processId = keyValue[0];
            int timestamp = Integer.parseInt(keyValue[1]);
            clock.put(processId, timestamp);
        }
        
        return clock;
    }

    private String getProcessId() {
        // Obtém o identificador do processo atual
        // Implemente a lógica para obter o ID do processo
        // ABC: Ainda não ta definido como os ids são criados pra poder pegar, se quiser pensar nisso
        return "???";
    }
}

