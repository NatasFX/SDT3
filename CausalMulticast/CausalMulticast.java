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

    private Map<Integer, ArrayList<Integer>> vectorClock = new HashMap<>(); // Relógio vetorial
    private Map<String, Boolean> buffer = new HashMap<>(); // Mensagens dentro do buffer terão seu valor de entrege aqui, com bool indicando se ja foi entrege
    private List<String> messageQueue = new ArrayList<>(); // Mensagens que ainda não foram enviadas
    private List<Integer> members = new ArrayList<>(); // Membros do grupo

    private InetAddress group; //grupo multicast
    private int port; // porta
    private ICausalMulticast client; // Referência do usuário para callback
    private MulticastSocket socket; // socket para dar send
    private Integer name; // nome da maquina

    private Thread thread;

    private int QNT_CLIENTES = 3;

    Scanner scanf;

    // inicializa tudo com -1, exceto o que representa esse processo que inicia com 0
    private void createVectorClock(Integer name) {
        vectorClock.put(name, new ArrayList<Integer>());
        for (int i = 0; i < QNT_CLIENTES; i++) {
            vectorClock.get(name).add(0);
        }
        if(this.name == name){
            vectorClock.get(name).set(name, 0);
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

        if (msg.startsWith("/sendAll")){
            for (String m : messageQueue) {
                send(m);
            }
            messageQueue.clear();
            return;
        }
        
        for (Integer nome : members) {
            if (nome.equals(name)) continue;
            String m = encode(nome.toString(), msg);
            
            if (ask("Devo enviar para \"" + nome + "\"?"))
            send(m);
            else messageQueue.add(m);
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

    private ArrayList<Integer> strToVC(String s) {
        s = s.replaceAll("\\[|\\]", "");
        ArrayList<Integer> temp_vc = new ArrayList<>();
        String[] clocks = s.split(",");

        for (String clock : clocks) {
            int valor = Integer.parseInt(clock.trim());
            temp_vc.add(valor);
        }

        return temp_vc;
    }

    // atualiza com base no algoritmo para estabilização de mensagens
    private void updateVectorClock(Integer sender, String VC) {
        ArrayList<Integer> array = strToVC(VC);

        vectorClock.put(sender, array);

        if (sender != name){
            vectorClock.get(name).set(sender, vectorClock.get(name).get(sender) + 1);
        }
    }

    // Verifica se é possível entregar mensagens do buffer de acordo com o relógio vetorial
    private void deliverMessagesFromBuffer() {
        List<String> removeFromBuffer = new ArrayList<>();

        // Criar uma cópia das chaves do buffer
        List<String> temp_buf = new ArrayList<>(buffer.keySet());

        for (String msg : temp_buf) {
            print("Analisando " + msg);

            String[] info = msg.split(":");
            ArrayList<Integer> msgClock = strToVC(info[3]);

            // mensagem já foi recebida, mas ainda esta no buffer
            if (buffer.containsKey(msg) && buffer.get(msg)) {
                tryToDiscardFromBuffer(msg, msgClock, Integer.decode(info[0]), removeFromBuffer);
                continue;
            }

            // mensagem ja foi liberada do buffer
            else if (!buffer.containsKey(msg)) {
                continue;
            }


            boolean canDeliver = true;

            for (int i = 0; i < QNT_CLIENTES; i++) {
                Integer vci_x = vectorClock.get(name).get(i);
                Integer vcmsg_x = msgClock.get(i);
                if (vcmsg_x > vci_x) {
                    print("Não pude entregar mensagem" + vcmsg_x + " " + vci_x + " " + i);
                    canDeliver = false;
                }
            }

            if (canDeliver) {
                // mensagem entregue
                buffer.put(msg, true);
                client.deliver(info[2]);
                updateVectorClock(Integer.decode(info[0]), info[3]);

                tryToDiscardFromBuffer(msg, msgClock, Integer.decode(info[0]), removeFromBuffer);

                for (String _msg : removeFromBuffer) {
                    buffer.remove(_msg);
                    buffer.remove(_msg);
                    String msgContent = _msg.split(":")[2];
                    System.out.println("\rMensagem liberada do buffer: " + msgContent);
                }

                // Chamar novamente a função para recomeçar a leitura do buffer
                deliverMessagesFromBuffer();
            }
        }

        removeFromBuffer.clear();
    }

    // mudar, ele precisa passar por todas mensagens do buffer e verificar
    private void tryToDiscardFromBuffer(String msg, ArrayList<Integer> msgClock, Integer sender, List<String> removeFromBuffer) {
        boolean canDiscard = true;

        int vcmsg = msgClock.get(sender);
        for (int i = 0; i < QNT_CLIENTES; i++) {
            int mci_x = vectorClock.get(i).get(sender);
            if (vcmsg > mci_x) {
                canDiscard = false;
            }
        }

        if (canDiscard) {
            removeFromBuffer.add(msg);
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
                    
                    buffer.put(s, false);

                    deliverMessagesFromBuffer(); // tenta entregar essa mensagem

                    print("Meu vetor logico agora: " + vectorClock.get(name).toString());
                    print("Minha matriz: \n" + vectorClock.get(0).toString() + "\n" +
                    vectorClock.get(1).toString() + "\n" + vectorClock.get(2).toString());
                }
            }
        }
    }
}

