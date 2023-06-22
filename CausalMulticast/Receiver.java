package CausalMulticast;

import java.net.DatagramPacket;
import java.net.MulticastSocket;
import java.util.ArrayList;
import java.util.List;

class Receiver extends Thread {

    String name;
    MulticastSocket socket;
    public List<String> messages = new ArrayList<String>();

    public Receiver(String name, MulticastSocket socket) {
        this.name = name;
        this.socket = socket;
    }

    private void print(String m) {
        System.out.println("[MIDDLEWARE] " + m);
    }


    private String decode(String msg) {
        String[] data = msg.split(":");
        if (data[0].equals(name))
            return data[1];
        else return "";
    }

    @Override
    public void run() {
        print("Início da execução da thread.");

        byte[] buf = new byte[1000];
        DatagramPacket recv = new DatagramPacket(buf, buf.length);
        while (true) {

            try {
                socket.receive(recv);
            } catch (Exception e) { e.printStackTrace(); return; }
            
            String s = decode(new String(recv.getData(), 0, recv.getLength()));

            if (s != "") {
                print("Mensagem para mim!!!!!!!!!!!!!!!!!!!!" + s);
            } else {
                print("nao pra mim");
            }

            
        }
    }
}
