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

    private int QNT_CLIENTES = 3;

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
            Thread.sleep(10);
            send(this.name.toString());
            
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

        if (!members.contains(0)) {
            print("Os nomes dos clientes devem ser de 0 até n continuamente");
            return;
        }

        // depois do while, todos os membros do multicast estão populados dentro de `members`
        print("Computadores conectados no grupo: " +  members.toString());
    }

    // ninguem gosta de system.out.meudeus.quanto.negocio.eu.so.quero.printar
    private void print(String m) {
        System.out.println("[MIDDLEWARE] " + m);
    }

    private String encode(String destinatario, String msg) {
        return name + ":" + destinatario + ":" + msg + ":" + vectorClock.get(name).toString();
    }

    private boolean message_for_me(String msg) {
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
        print("Meu vetor logico: "+vectorClock.get(name).toString());
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

    private void incrementVectorClock(Integer processId) {
        vectorClock.get(name).set(processId, vectorClock.get(name).get(processId) + 1);
    }

    private int[] strToVC(String s) {
        s = s.replaceAll("\\[|\\]", "");
        return Arrays.stream(s.split(", "))
            .mapToInt(Integer::parseInt)
            .toArray();
    }

    private void updateVectorClock(Integer sender, String VC) {
        vectorClock.get(name).clear();
        
        int[] array = strToVC(VC);

        for (int k : array) {
            vectorClock.get(name).add(k);
        }

        if (sender != name) // esse if é irrelevante mas vou colocar no código msm assim pq ta na especificacao
            incrementVectorClock(sender);
    }

    // Verifica se é possível entregar mensagens do buffer de acordo com o relógio vetorial
    private void deliverMessagesFromBuffer() {
        List<String> messagesToDeliver = new ArrayList<>();

        List<String> temp_buf = new ArrayList<>();
        temp_buf.addAll(buffer);

        for (String msg : temp_buf) {
            print("Analizando " + msg);
            int[] msgClock = strToVC(msg.split(":")[3]);
            
            boolean candeliver = true;

            for (int i = 0; i < QNT_CLIENTES; i++) {
                Integer vci_x = vectorClock.get(name).get(i);
                int vcmsg_x = msgClock[i];
                if (vcmsg_x > vci_x) {
                    print("Não pude entregar mensagem");
                    candeliver = false;
                }
            }

            if (candeliver) {
                String[] info = msg.split(":");
                updateVectorClock(Integer.decode(info[0]), info[3]);
                client.deliver(info[2]);
                buffer.remove(msg);

                // chama novamente para que tente entregar outra mensagem, se não entregar nenhuma não faz mal
                deliverMessagesFromBuffer(); 
            }
        }

        // Remove mensagens do buffer e entrega ao cliente
        /*
         * Falta implementar essa parte de estabilização de mensagens
        */
        for (String msg : messagesToDeliver) {
            buffer.remove(msg);
            client.deliver(msg);
        }
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

                        QNT_CLIENTES += 1; // essa parte aqui tá bem rudimentar, precisa ser melhorada
                        
                        try {
                            findOtherClients();
                        } catch (Exception e) { e.printStackTrace(); }

                        print("Adicionado novo membro na computação: " + s);
                        continue;
                    }
                }

                
                if (message_for_me(s)) {
                    
                    print("Vetor lógico em piggyback da mensagem recebida: " + s.split(":")[3]);
                    
                    buffer.add(s);
                    deliverMessagesFromBuffer(); // tenta entregar essa mensagem

                    print("Meu vetor logico agora: " + vectorClock.get(name).toString());
                }
            }
        }
    }
}

