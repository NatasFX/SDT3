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
        System.out.println("\r[MIDDLEWARE] " + m);
    }


    private boolean decode(String msg) {
        String[] data = msg.split(":");
        return data[0].equals(name) && msg.contains(":");
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

            if (decode(s)) {
                print("Recebido: " + s);
            } else {
                // print("");
            }

            
        }
    }
}
