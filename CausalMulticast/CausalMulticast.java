package CausalMulticast;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CausalMulticast {
    private Map<String, Integer> vectorClock; // Relógio vetorial
    private List<String> buffer; // Buffer de mensagens
    private List<ICausalMulticast> members; // Membros do grupo
    private String multicastGroup;
    private int multicastPort;
    private ICausalMulticast client; // Referência do usuário para callback
    

    public CausalMulticast(String ip, Integer port, ICausalMulticast client, String multicastGroup, int multicastPort) {
        // Inicialização do middleware
        this.vectorClock = new HashMap<>();
        this.buffer = new ArrayList<>();
        this.members = new ArrayList<>();
        this.client = client;
        this.multicastGroup = multicastGroup;
        this.multicastPort = multicastPort;

        // Iniciar a descoberta dos membros do grupo
        startMembershipDiscovery(ip, port);
    }

    private void startMembershipDiscovery(String ip, Integer port) {
    /* ABC: ver como implementar uma forma de descobrir membror do grupo, considerar a especificaçao: 
    "O serviço de descoberta deve permanecer sempre ativo, a fim de permitir atualização dinâmica
    dos membros do grupo"
    */ 

    // Adicione os membros do grupo à lista 'members'

    // Inicie a conexão com o grupo multicast
    joinMulticastGroup();
    }

    public void mcsend(String msg, ICausalMulticast client) {
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
        for (ICausalMulticast member : members) {
            member.deliver(msg);
        }
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


    private MulticastSocket multicastSocket;

    private void joinMulticastGroup() {
        try {
            multicastSocket = new MulticastSocket(multicastPort);

            InetAddress groupAddress = InetAddress.getByName(multicastGroup);
            multicastSocket.joinGroup(groupAddress);

            // Inicie uma thread para receber mensagens multicast
            Thread receiveThread = new Thread(this::receiveMulticastMessages);
            receiveThread.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void sendMulticastMessage(String msg) {
        try {
            // Construa o pacote de dados para enviar a mensagem multicast
            byte[] buffer = msg.getBytes();
            InetAddress groupAddress = InetAddress.getByName(multicastGroup);
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, groupAddress, multicastPort);

            // Envie a mensagem multicast pelo socket multicast
            multicastSocket.send(packet);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void receiveMulticastMessages() {
        byte[] bufferBytes = new byte[1024];

        try {
            while (true) {
                DatagramPacket packet = new DatagramPacket(bufferBytes, bufferBytes.length);
                multicastSocket.receive(packet);

                // Extrai a mensagem recebida do pacote e realiza o processamento necessário
                String msg = new String(packet.getData(), 0, packet.getLength());

                // Chame o método 'deliver' para entregar a mensagem ao CausalMulticast
                deliver(msg, new HashMap<>());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}

