package CausalMulticast;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;


/**
 * Classe principal.
 */
public class CausalMulticast {

    private Map<String, Map<String, Integer>> vectorClock = new HashMap<>(); // Relógio vetorial
    private List<Mensagem> buffer = new ArrayList<>();       // Buffer de mensagens
    private List<String> messageQueue = new ArrayList<>();  // Mensagens que ainda não foram enviadas
    private List<String> members = new ArrayList<>();      // Membros do grupo

    private InetAddress group;       // Grupo multicast
    private int port;                // Porta
    private ICausalMulticast client; // Referência do usuário para callback
    private DatagramSocket socketUnicast;  // socket para dar send
    private MulticastSocket socket;  // socket para dar send
    private String name;            // nome da maquina

    private Thread thread;

    private int QNT_CLIENTES = 1;

    Scanner scanf = new Scanner(System.in);


    /**
    * Classe para representação da mensagem e seu estado.
    */
    class Mensagem {
        protected String content;
        protected boolean delivered = false;
        protected String destino;
        protected Map<String, Integer> VC = new HashMap<String, Integer>();
        protected String origem;

        /**
        * Cosntrutor da classe.
        */
        public Mensagem(String content, String ip_destino) {
            this.origem = name;
            this.content = content;
            this.delivered = true;
            this.destino = ip_destino;
        }

        // para recebimento
        public Mensagem(String content) {
            String[] info = content.split(":");
            this.origem = info[0];
            this.content = info[1];
            this.delivered = false;
            VC = strToVC(info[2]);
        }

        public String toString() {
            return "Para: " + destino + " : " + content;
        }

        public void send_unicast() {
            String encoded = encode(content);
            try {
                DatagramPacket packet = new DatagramPacket(encoded.getBytes(), encoded.length(), InetAddress.getByName(destino), 9000);
                socketUnicast.send(packet);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public void send_raw_unicast(String VC) {
            String s = name + ":" + content + ":" + VC;
            try {
                DatagramPacket packet = new DatagramPacket(s.getBytes(), s.length(), InetAddress.getByName(destino), 9000);
                socketUnicast.send(packet);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
    }


    /**
    * inicializa tudo com -1, exceto o que representa esse processo que inicia com 0.
    * @param name Inteiro que representa o cliente
    */
    private void createVectorClock(String name) {
        
        if (!members.contains(name)) {
            print("Adicionado novo membro no grupo: " + name);

            members.add(name);
            Collections.sort(members);
        }

        if (!vectorClock.containsKey(name)) {
            vectorClock.put(name, new HashMap<String, Integer>());
        }

        for (String ip1 : members)
            for (String ip2 : members) {
                vectorClock.get(ip1).put(ip2, 0);
            }

        print("final: " + vectorClock.toString());
    }

    /**
    * Construtor principal da classe.
    * @param ip Inteiro que representa o cliente
    * @param port Inteiro que representa a porta utilizada para comunicação
    * @param client Referência para o cliente
    */
    public CausalMulticast(String ip, Integer port, ICausalMulticast client) {
        // Inicialização do middleware
        try {
            this.socketUnicast = new DatagramSocket(4000);
        } catch (Exception e) {
            print("O middleware exige que os nodos tenham seus próprios endereços IP! Sem eles não existe comunicação unicast.");
            return;
        }

        this.client = client;
        this.port = port;

        String ip_;

        try {
            ip_ = InetAddress.getLocalHost().getHostAddress();
            try (final DatagramSocket asocket = new DatagramSocket()) {
                asocket.connect(InetAddress.getByName("8.8.8.8"), 10002);
                ip_ = asocket.getLocalAddress().getHostAddress();
            }
            this.name = ip_;
        } catch (Exception e) { e.printStackTrace(); }
        
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
        send_multicast(this.name);

        byte[] buf = new byte[1000];
        DatagramPacket recv = new DatagramPacket(buf, buf.length);

        while (members.size() < QNT_CLIENTES) {
            socket.receive(recv);
            
            String data = new String(recv.getData(), 0, recv.getLength());
            
            if (name.equals(data)) {
                continue;
            } else {
                createVectorClock(data);
            }

            Thread.sleep(300);
            send_multicast(this.name);
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
    private String encode(String msg) {
        return name + ":" + msg + ":" + vectorClock.get(name).toString();
        // 0 sender
        // 1 msg
        // 2 vc
    }

    /**
    * Função que envia a mensagem no canal multicast.
    * @param msg String contendo a mensagem a ser enviada
    */
    private void send_multicast(String msg) {
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
            for (String msg_ : messageQueue) {
                String[] info = msg_.split(":");
                new Mensagem(info[2], info[0]).send_raw_unicast(info[3]);
            }
            messageQueue.clear();
            return;
        }
        else if(msg.startsWith("/buffer")){
            for (Mensagem m : buffer) {
                System.out.print(" [" + m.content + "] ");
            }
            return;
        }
        
        for (String ip : members) {
            if (ip.equals(name)) continue;
            Mensagem m = new Mensagem(msg, ip);
            
            if (ask("Devo enviar para \"" + ip + "\"?"))
                m.send_unicast();
            else messageQueue.add(ip+":"+encode(msg));
        }

        // Incrementa o relógio vetorial
        buffer.add(new Mensagem(msg, name));
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
    private void incrementVectorClock(String processId) {
        vectorClock.get(name).put(processId, vectorClock.get(name).get(processId) + 1);
    }

    /**
    * Função auxiliar para converter a representação do vetor lógico em piggyback recebido na mensagem
    * que está em string, para um ArrayList<Integer> o qual podemos utilizar.
    * @param s Representação em string do ArrayList<Integer> para conversão
    */
    private Map<String, Integer> strToVC(String s) {
        s = s.replaceAll("\\{|\\}", "");
        String[] clocks = s.split(",");
        
        Map<String, Integer> ret = new HashMap<String, Integer>();

        for (String entry : clocks) {
            entry = entry.trim();
            String[] k = entry.split("=");
            String ip = k[0];
            Integer value = Integer.decode(k[1]);

            ret.put(ip, value);
        }

        return ret;
    }

    /**
    * Função que atualiza com base no algoritmo para estabilização de mensagens.
    * @param sender Inteiro que representa o remetente
    * @param VC String que foi recebida na mensagem original que representa o vetor em piggyback
    */
    private void updateVectorClock(String sender, Map<String, Integer> array) {
        vectorClock.put(sender, array);

        if (!name.equals(sender)){
            vectorClock.get(name).put(sender, vectorClock.get(name).get(sender) + 1);
        }
    }

    /**
    * Função que verifica se é possível entregar mensagens do buffer de acordo com o relógio vetorial.
    */
    private void causalOrder() {
        for (Mensagem msg : buffer) {
            if(!msg.delivered){

                boolean canDeliver = true;
                for (String ip : members) {
                    if (msg.VC.get(ip) > vectorClock.get(name).get(ip))
                        canDeliver = false;
                }

                // boolean canDeliver = members.forEach()
                //     .allMatch(i -> msgClock.get(i) <= vectorClock.get(name).get(i));

                if (canDeliver) {
                    client.deliver(msg.origem + ": " + msg.content);
                    msg.delivered = true;
                    updateVectorClock(msg.origem, msg.VC);
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
            Mensagem msg = buffer.get(index);
            if (msg.delivered) {
                String sender = msg.origem;
                boolean canDiscard = true;

                if (!name.equals(sender)) {
                    Integer vcmsg = msg.VC.get(sender);
                    
                    for (String ips : members) {
    
                        if (name.equals(ips)) continue;
    
                        Integer mci_x = vectorClock.get(ips).get(sender);
    
                        if (vcmsg >= mci_x) {
                            canDiscard = false;
                            break;
                        }
                    }
                }

                if (canDiscard) {
                    buffer.remove(msg);
                    print("Mensagem liberada do buffer: \"" + msg.origem + ": " + msg.content + "\"");
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
            Map<String, Integer> vc1List = msg1.VC;
            Map<String, Integer> vc2List = msg2.VC;

            vc1List = fillVectorClock(vc1List);
            vc2List = fillVectorClock(vc2List);

            if (vc1List == vc2List) return 0;

            int sum0 = 0;
            int sum1 = 0;

            for (int value : vc1List.values()) {
                sum0 += value;
            }

            for (int value : vc2List.values()) {
                sum1 += value;
            }

            return Integer.compare(sum0, sum1);
        });
    }

    private ArrayList<Integer> fillVectorClock(ArrayList<Integer> vectorClock){
        if (vectorClock.size() < QNT_CLIENTES) {
            int diff = QNT_CLIENTES - vectorClock.size();
            for (int i = 0; i < diff; i++) {
                vectorClock.add(0);
            }
        }
        return vectorClock;
    }

    /**
    * Classe para receber as mensagens de forma assíncrona.
    */
    class Receiver extends Thread {

        String name;
        MulticastSocket socket;
        private List<String> members;
        
        public List<String> messages = new ArrayList<String>();
        
        /**
        * Construtor da classe.
        */
        public Receiver(String name_, MulticastSocket socket, List<String> members) {
            this.name = name_;
            this.socket = socket;
            this.members = members;
        }

        @Override
        public void run() {
            byte[] buf = new byte[1000];
            
            DatagramPacket recv = new DatagramPacket(buf, buf.length);
            while (true) {
                
                try {
                    socket.receive(recv);
                } catch (Exception e) {}
                
                String s = new String(recv.getData(), 0, recv.getLength());

                if (!s.contains(":")) { // mensagem inicial
                    if (!members.contains(s)) {

                        QNT_CLIENTES += 1;

                        try {
                            findOtherClients();
                        } catch (Exception e) { e.printStackTrace(); }

                    }
                    continue;
                }

                print("Vetor lógico em piggyback da mensagem recebida: " + s.split(":")[2]);
                
                buffer.add(new Mensagem(s));
                bufferSort();
                
                causalOrder();
                estabilização();

                print("Minha matriz:");
                for (String ips : members) {
                    if (name.equals(ips))
                        print("\u001B[32m" + vectorClock.get(ips).toString() + "\u001B[0m");
                    else 
                        print(vectorClock.get(ips).toString());
                }
            }
        }
    }
}

