package CausalMulticast;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public class CausalMulticast {

    private Map<Integer, ArrayList<Integer>> vectorClock = new HashMap<>(); // Relógio vetorial
    private List<String> buffer = new ArrayList<>(); // Buffer de mensagens
    private List<Integer> members = new ArrayList<>(); // Membros do grupo

    private InetAddress group; //grupo multicast
    private int port; // porta
    private ICausalMulticast client; // Referência do usuário para callback
    private MulticastSocket socket; // socket para dar send
    private Integer name; // nome da maquina

    private Thread thread;

    private int QNT_CLIENTES = 2;

    Scanner scanf;

    private void createVectorClock(Integer name) {
        vectorClock.put(name, new ArrayList<Integer>());
        for (int i = 0; i < QNT_CLIENTES; i++) {
            vectorClock.get(name).add(0);
        }
    }

    public CausalMulticast(String ip, Integer port, ICausalMulticast client) {
        // Inicialização do middleware
        this.client = client;
        this.port = port;

        this.scanf = new Scanner(System.in);
        print("Qual o nome da sua máquina?");
        String nome = scanf.nextLine();

        try {
            this.name = Integer.decode(nome);
        } catch (Exception e) {
            print("Nome inválido, abortando!");
            return;
        }

        members.add(name);
        createVectorClock(name);

        // criar grupo e entrar nele
        try {
            this.group = InetAddress.getByName(ip);
            this.socket = new MulticastSocket(port);
            socket.joinGroup(group);

            findOtherClients();
        } catch (Exception e) {
            print("Erro ao criar/entrar grupo multicast " + e.toString());
        }

        this.thread = new Receiver(name, socket, client, members);

        thread.start();
    }

    private void findOtherClients() throws Exception {
        print("Detectando outras máquinas...");

        byte[] buf = new byte[1000];

        while (members.size() < QNT_CLIENTES) {
            send(this.name.toString());
            Thread.sleep(100);
            
            DatagramPacket recv = new DatagramPacket(buf, buf.length);

            socket.receive(recv);

            Integer data = Integer.decode(new String(recv.getData(), 0, recv.getLength()));

            if (name == data) {
                continue;
            } else {
                if (!members.contains(data)) {
                    print("Encontrado \"" + data + "\"");
                    members.add(data);
                    createVectorClock(data);
                }
            }
        }
        // depois do while, todos os membros do multicast estão populados dentro de `members`
        print("Computadores conectados no grupo: " + '"' +  members.toString() + '"');
    }

    // ninguem gosta de system.out.meudeus.quanto.negocio.eu.so.quero.printar
    private void print(String m) {
        System.out.println("[MIDDLEWARE] " + m);
    }

    private String encode(String destinatario, String msg) {
        return name + ":" + destinatario + ":" + msg + ":" + vectorClock.toString();
    }

    private boolean decode(String msg) {
        if (!msg.contains(":")) return false;
        
        String[] data = msg.split(":");
        return Integer.decode(data[1]) == name;
    }

    private void send(String msg) {

        DatagramPacket packet = new DatagramPacket(msg.getBytes(), msg.length(), group, port);
        try {
            socket.send(packet);
        } catch (Exception e) { e.printStackTrace(); }
    }

    public void mcsend(String msg, ICausalMulticast client) {

        if (msg.contains(":")) {
            print("Mensagem não pode conter \":\". Cancelando envio");
            return;
        }
        
        /* ABC Implementar:
        *  Para possibilitar a correção do trabalho, faça o envio de cada mensagem unicast ser
        *  controlado via teclado, ou seja, deve haver uma pergunta antes de cada envio unicast
        *  (controle) questionando se é para enviar a todos ou não.
        *  se for multicast, usar sendMulticastMessage(msg)
        */
        
        List<Integer> nao_enviados = new ArrayList<>();
        
        for (Integer nome : members) {
            if (nome.equals(name)) continue;
            String m = encode(nome.toString(), msg);
            
            if (ask("Devo enviar para \"" + nome + "\"?"))
            send(m);
            else nao_enviados.add(nome);
        }
        
        if (nao_enviados.size() != 0) {
            print("Faltou enviar alguns");
            int k = 0;
            while (k < nao_enviados.size()) {
                String m = encode(nao_enviados.get(k).toString(), msg);
                
                if (ask("Devo enviar para \"" + nao_enviados.get(k) + "\"?")) {
                    send(m);
                    k++;
                }
            }
        }

        // Incrementa o relógio vetorial
        incrementVectorClock(name);
    }
    
    private boolean ask(String m) {
        while (true) {
            print(m+" [y/n]");
            String res = scanf.nextLine();
            if (res.equals("y")) {
                return true;
            } else if (res.equals("n")) {
                return false;
            }
        }
    }

    public void deliver(String msg, Map<String, Integer> senderClock) {
        // Atualiza o relógio vetorial com o relógio do remetente
        // updateVectorClock(senderClock);

        // Adiciona a mensagem ao buffer
        buffer.add(msg);

        // Verifica se é possível entregar mensagens do buffer
        deliverMessagesFromBuffer();
    }

    private void incrementVectorClock(Integer processId) {
        vectorClock.get(name-1).set(processId-1, vectorClock.get(name-1).get(processId-1) + 1);
    }

    private void updateVectorClock(Integer sender, String VC) {
        
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


    class Receiver extends Thread {

        Integer name;
        MulticastSocket socket;
        ICausalMulticast client;
        private List<Integer> members;
        
        public List<String> messages = new ArrayList<String>();

        public Receiver(Integer name, MulticastSocket socket, ICausalMulticast client, List<Integer> members) {
            this.name = name;
            this.socket = socket;
            this.client = client;
            this.members = members;
        }

        private void print(String m) {
            System.out.println("\r[MIDDLEWARE] " + m);
        }

        @Override
        public void run() {
            byte[] buf = new byte[1000];

            DatagramPacket recv = new DatagramPacket(buf, buf.length);
            while (true) {

                try {
                    socket.receive(recv);
                } catch (Exception e) { e.printStackTrace(); return; }
                
                String s = new String(recv.getData(), 0, recv.getLength());

                if (!s.contains(":")) { // mensagem inicial
                    if (!members.contains(Integer.decode(s))) {
                        QNT_CLIENTES += 1;
                        try {
                            findOtherClients();
                        } catch (Exception e) { e.printStackTrace(); }
                        print("Adicionado novo membro na computação: " + s);
                        continue;
                    }
                }

                buffer.add(s);

                if (decode(s)) {
                    String[] info = s.split(":");
                    // updateVectorClock(info[0], info[3]);
                    print("Vetor lógico em piggyback da mensagem recebida: " + info[3]);
                    client.deliver("De: \"" + info[0] + "\" \"" + info[2] + "\"");
                } else {
                    // print("");
                }
            }
        }
    }
}

