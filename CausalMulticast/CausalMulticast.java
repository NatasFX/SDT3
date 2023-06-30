package CausalMulticast;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.stream.IntStream;

/**
 * Classe principal.
 */
public class CausalMulticast {

    private Map<Integer, ArrayList<Integer>> vectorClock = new HashMap<>(); // Relógio vetorial
    private List<Message> buffer = new ArrayList<>();       // Buffer de mensagens
    private List<String> messageQueue = new ArrayList<>();  // Mensagens que ainda não foram enviadas
    private List<Integer> members = new ArrayList<>();      // Membros do grupo

    private InetAddress group;       // Grupo multicast
    private int port;                // Porta
    private ICausalMulticast client; // Referência do usuário para callback
    private MulticastSocket socket;  // socket para dar send
    private Integer name;            // nome da maquina

    private Thread thread;

    private int QNT_CLIENTES = 1;

    Scanner scanf = new Scanner(System.in);

    /**
    * inicializa tudo com -1, exceto o que representa esse processo que inicia com 0.
    * @param name Inteiro que representa o cliente
    */
    private void createVectorClock(Integer name) {
        
        if (!vectorClock.containsKey(name)) {
            vectorClock.put(name, new ArrayList<Integer>());
            
            for (int i = 0; i < QNT_CLIENTES; i++)
                if (vectorClock.containsKey(i))
                    while (vectorClock.get(i).size() < QNT_CLIENTES)
                        vectorClock.get(i).add(0);
            
        }
        
        if (!members.contains(name)) {
            print("Adicionado novo membro no grupo: " + name);

            members.add(name);
            Collections.sort(members);
        }
    }

    /**
    * Construtor principal da classe.
    * @param ip Inteiro que representa o cliente
    * @param port Inteiro que representa a porta utilizada para comunicação
    * @param client Referência para o cliente
    */
    public CausalMulticast(String ip, Integer port, ICausalMulticast client) {
        // Inicialização do middleware
        this.client = client;
        this.port = port;

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

        this.thread = new Receiver(name, socket, members);

        thread.start();
    }

    /**
    * Encontra os outros clientes que estão conectados no mesmo canal multicast.
    */
    private void findOtherClients() throws Exception {
        send(this.name.toString());

        byte[] buf = new byte[1000];
        DatagramPacket recv = new DatagramPacket(buf, buf.length);

        while (members.size() < QNT_CLIENTES) {
            socket.receive(recv);
            
            Integer data = Integer.decode(new String(recv.getData(), 0, recv.getLength()));
            
            if (name == data) {
                continue;
            } else {
                createVectorClock(data);
            }

            Thread.sleep(300);
            send(this.name.toString());
        }

        // depois do while, todos os membros do multicast estão populados dentro de `members`
        print("Computadores conectados no grupo: " +  members.toString());
    }

    /**
    * Função auxiliar para printar bonito no terminal.
    */
    private void print(String m) {
        System.out.println("[MIDDLEWARE] " + m);
    }

    /**
    * Função que codifica dados para uma string que possa ser enviada para outro cliente,
    * de forma que seja possível extrair de volta as informações originais.
    * @param destinatario Inteiro que representa o nome do destinatario
    * @param msg String contendo a mensagem a ser enviada
    */
    private String encode(Integer destinatario, String msg) {
        return name + ":" + destinatario + ":" + msg + ":" + vectorClock.get(name).toString();
    }

    /**
    * Função que define a mensagem é destinada ao cliente atual.
    * @param msg String contendo a mensagem para análise
    */
    private boolean message_for_me(String msg) {
        if (!msg.contains(":")) return false;
        
        String[] data = msg.split(":");
        return Integer.decode(data[1]) == name;
    }

    /**
    * Função que envia a mensagem no canal multicast.
    * @param msg String contendo a mensagem a ser enviada
    */
    private void send(String msg) {
        DatagramPacket packet = new DatagramPacket(msg.getBytes(), msg.length(), group, port);
        try {
            socket.send(packet);
        } catch (Exception e) { e.printStackTrace(); }
    }

    /**
    * Função exposta ao cliente para ele enviar uma mensagem quando solicitado.
    * @param msg String contendo a mensagem a ser enviada
    * @param msg Referência ao cliente
    */
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
            String m = encode(nome, msg);
            
            if (ask("Devo enviar para \"" + nome + "\"?"))
                send(m);
            else messageQueue.add(m);
        }

        // Incrementa o relógio vetorial
        buffer.add(new Message(encode(name, msg), true));
        incrementVectorClock(name);
    }
    
    /**
    * Função auxiliar para o middleware pedir algo ao cliente.
    * @param m Mensagem a ser exibida no terminal contendo a pergunta a ser respondida com [sim/nao]
    */
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

    /**
    * Função que incrementa o vetor de relógio lógico do processo fornecido.
    * @param processId Inteiro que representa o processo
    */
    private void incrementVectorClock(Integer processId) {
        vectorClock.get(name).set(processId, vectorClock.get(name).get(processId) + 1);
    }

    /**
    * Função auxiliar para converter a representação do vetor lógico em piggyback recebido na mensagem
    * que está em string, para um ArrayList<Integer> o qual podemos utilizar.
    * @param s Representação em string do ArrayList<Integer> para conversão
    */
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

    /**
    * Função que atualiza com base no algoritmo para estabilização de mensagens.
    * @param sender Inteiro que representa o remetente
    * @param VC String que foi recebida na mensagem original que representa o vetor em piggyback
    */
    private void updateVectorClock(Integer sender, String VC) {
        ArrayList<Integer> array = strToVC(VC);

        vectorClock.put(sender, array);

        if (sender != name){
            vectorClock.get(name).set(sender, vectorClock.get(name).get(sender) + 1);
        }
    }

    /**
    * Função que verifica se é possível entregar mensagens do buffer de acordo com o relógio vetorial.
    */
    private void causalOrder() {
        for (Message msg : buffer) {
            if(!msg.isDelivered()){
                String[] info = msg.getContent().split(":");
                ArrayList<Integer> msgClock = strToVC(info[3]);

                boolean canDeliver = IntStream.range(0, QNT_CLIENTES)
                    .allMatch(i -> msgClock.get(i) <= vectorClock.get(name).get(i));

                if (canDeliver) {
                    client.deliver(info[0] + ": " + info[2]);
                    msg.setDelivered(true);
                    updateVectorClock(Integer.decode(info[0]), info[3]);
                }  else {
                    print("Não pude entregar mensagem");
                }
            }
        }
    }

    /**
    * Função que descarta mensagens que já foram entregues e estão estabilizadas.
    */
    private void estabilização() {
        for (int index = 0; index < buffer.size(); index++) {
            Message msg = buffer.get(index);
            if (msg.isDelivered()) {
                boolean canDiscard = true;
                String[] info = msg.getContent().split(":");

                Integer sender = Integer.decode(info[0]);

                Integer vcmsg = strToVC(info[3]).get(sender);
                for (int i = 0; i < QNT_CLIENTES; i++) {

                    if (i == sender) continue;

                    Integer mci_x = vectorClock.get(i).get(sender);

                    if (vcmsg >= mci_x) {
                        canDiscard = false;
                        break;
                    }
                }

                if (canDiscard) {
                    buffer.remove(msg);
                    print("Mensagem liberada do buffer: \"" + info[0] + ": " + info[2] + "\"");
                    index--;
                }
            }
        }
    }
    
    /**
    * Função que ordena o buffer de acordo com a ordem dos vetores de piggyback.
    */
    private void bufferSort() {
        buffer.sort((msg1, msg2) -> {
            ArrayList<Integer> vc1List = strToVC(msg1.getContent().split(":")[3]);
            ArrayList<Integer> vc2List = strToVC(msg2.getContent().split(":")[3]);

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

    /**
    * Classe para receber as mensagens de forma assíncrona.
    */
    class Receiver extends Thread {

        Integer name;
        MulticastSocket socket;
        private List<Integer> members;
        
        public List<String> messages = new ArrayList<String>();
        
        /**
        * Construtor da classe.
        */
        public Receiver(Integer name, MulticastSocket socket, List<Integer> members) {
            this.name = name;
            this.socket = socket;
            this.members = members;
        }

        /**
        * Função auxiliar para print.
        */
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

                        continue;
                    }
                }

                
                if (message_for_me(s)) {
                    
                    print("Vetor lógico em piggyback da mensagem recebida: " + s.split(":")[3]);
                    
                    buffer.add(new Message(s, false));
                    bufferSort();
                    
                    causalOrder();
                    estabilização();

                    print("Minha matriz:");
                    for (int i = 0; i < QNT_CLIENTES; i++) {
                        if (i == name)
                            print("\u001B[32m" + vectorClock.get(i).toString() + "\u001B[0m");
                        else 
                            print(vectorClock.get(i).toString());
                    }
                }
            }
        }
    }

    /**
    * Classe para representação da mensagem e seu estado.
    */
    class Message {
        private String content;
        private boolean delivered;

        /**
        * Cosntrutor da classe.
        */
        public Message(String content, boolean delivered) {
            this.content = content;
            this.delivered = delivered;
        }

        /**
        * Retorna o conteúdo da mensagem.
        */
        public String getContent() {
            return content;
        }

        /**
        * Retorna se a mensagem já foi entregue ou não.
        */
        public boolean isDelivered() {
            return delivered;
        }

        /**
        * Indica se a mensagem tem flag entregue ou não.
        */
        public void setDelivered(boolean delivered) {
            this.delivered = delivered;
        }
    }
}

