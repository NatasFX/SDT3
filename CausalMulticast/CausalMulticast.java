package CausalMulticast;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.stream.IntStream;

public class CausalMulticast {

    private Map<Integer, ArrayList<Integer>> vectorClock = new HashMap<>(); // Relógio vetorial
    private List<Message> buffer = new ArrayList<>(); // Buffer de mensagens
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
            vectorClock.get(name).add(-1);
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
        else if(msg.startsWith("/buffer")){
            for (Message m : buffer) {
                System.out.print(" [" + m.getContent() + "] ");
            }
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
    private void causalOrder() {
        for (Message msg : buffer) {
            if(!msg.isDelivered()){
                String[] info = msg.getContent().split(":");
                ArrayList<Integer> msgClock = strToVC(info[3]);

                boolean canDeliver = IntStream.range(0, QNT_CLIENTES)
                    .allMatch(i -> msgClock.get(i) <= vectorClock.get(name).get(i));

                if(canDeliver){
                    client.deliver(info[2]);
                    msg.setDelivered(true);
                }
                else{
                    print("Não pude entregar mensagem");
                }
            }
        }
    }

    private void stabilization(){
        for (int index = 0; index < buffer.size(); index++) {
            Message msg = buffer.get(index);
            if (msg.isDelivered()) {
                boolean canDiscard = true;
                String[] info = msg.getContent().split(":");

                Integer vcmsg = strToVC(info[3]).get(Integer.decode(info[0]));
                for (int i = 0; i < QNT_CLIENTES; i++) {
                    Integer mci_x = vectorClock.get(i).get(Integer.decode(info[0]));
                    if(vcmsg > mci_x){
                        canDiscard = false;
                    }
                }

                if(canDiscard){
                    buffer.remove(msg);
                    System.out.println("\rMensagem liberada do buffer: " + info[2]);
                    index--;
                }
            }
        }
    }
    
    // ordenamento ta calculando errado por causa dos -1
    private void bufferSort() {
        buffer.sort((msg1, msg2) -> {
            ArrayList<Integer> vc1List = strToVC(msg1.getContent().split(":")[3]);
            ArrayList<Integer> vc2List = strToVC(msg1.getContent().split(":")[3]);

            if (vc1List == vc2List) return 0;
            int sum0 = 0;
            int sum1 = 0;
            for (int i = 0; i < vc1List.size(); i++) {
                sum0 += vc1List.get(i);
                sum1 += vc2List.get(i);
            }
            return Integer.compare(sum0, sum1);
        });
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
                    
                    buffer.add(new Message(s, false));
                    bufferSort();
                    String[] info = s.split(":");
                    updateVectorClock(Integer.decode(info[0]), info[3]);
                    
                    causalOrder();
                    stabilization();

                    print("Meu vetor logico agora: " + vectorClock.get(name).toString());
                    print("Minha matriz: \n" + vectorClock.get(0).toString() + "\n" +
                    vectorClock.get(1).toString() + "\n" + vectorClock.get(2).toString());
                }
            }
        }
    }

    class Message {
        private String content;
        private boolean delivered;

        public Message(String content, boolean delivered) {
            this.content = content;
            this.delivered = delivered;
        }

        public String getContent() {
            return content;
        }

        public boolean isDelivered() {
            return delivered;
        }

        public void setDelivered(boolean delivered) {
            this.delivered = delivered;
        }
    }
}

