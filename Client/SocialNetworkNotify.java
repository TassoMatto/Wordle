package Client;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;


public class SocialNetworkNotify implements Runnable {
    
    private WordleClient wc;
    private String ip;
    private int port;

    public SocialNetworkNotify(WordleClient wc, String ip, int port) {
        this.wc = wc;
        this.ip = ip;
        this.port = port;
    }

    @Override
    public void run() {
        MulticastSocket ms = null;
        InetSocketAddress isa = null;
        NetworkInterface ni = null;
        byte[] buf = new byte[1024];
        try {
            isa = new InetSocketAddress(InetAddress.getByName(ip), port);
            ni = NetworkInterface.getByName("wlan0");
            ms = new MulticastSocket(port);
            ms.setNetworkInterface(ni);
            ms.joinGroup(isa, ni);
            System.out.println(ip + " " + port);
            while (!Thread.currentThread().isInterrupted()) {
                DatagramPacket dp = new DatagramPacket(buf, buf.length);
                ms.receive(dp);
                String msg = new String(dp.getData(), 0, dp.getLength());
                this.wc.addNotify(msg);
                System.out.println("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
            }    
        } catch (Exception e) {
            e.printStackTrace();
            return;
        } finally {
            if(ms != null) {
                ms.close();
                try {
                    ms.leaveGroup(isa, ni);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

    }

    

}
