package Client;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.util.Enumeration;

/**
 * 
 * @class                       SocialNetworkNotify
 * @brief                       Classe che simula il social network su cui condividere i risultati
 * @author                      Simone Tassotti
 * @date                        19/04/2023
 * 
 */
public class SocialNetworkNotify implements Runnable {
    
    /** Attributi oggetto */
    private WordleClient wc;                // Client che vuole comunicare con il social network
    private String ip;                      // Indirizzo del social network (socket multicast)
    private int port;                       // Porta del social network 

    /**
     * 
     * @fun                             SocialNetworkNotify
     * @brief                           Metodo costruttore
     * @param wc                        Client che comunica con il social network
     * @param ip                        Indirizzo ip del gruppo multicast
     * @param port                      Porta del gruppo multicast
     * 
     */
    public SocialNetworkNotify(WordleClient wc, String ip, int port) {
        this.wc = wc;
        this.ip = ip;
        this.port = port;
    }

    /**
     * 
     * 
     * 
     */
    public static NetworkInterface setInterface (MulticastSocket multicastSocket) throws IOException {

        /** Cerco un'interfaccia disponibile  */
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface i = interfaces.nextElement();
            Enumeration<InetAddress> addresses = i.getInetAddresses();
            while (addresses.hasMoreElements()) {
                InetAddress address = addresses.nextElement();
                if (address instanceof Inet4Address) {
                    multicastSocket.setNetworkInterface(i);
                    return i;
                }
            }
        }
        return null;
    }

    /**
     * 
     * @fun                             run
     * @brief                           Si occupa di ricevere e gestire le condivisioni sul social network
     * 
     */
    @Override
    public void run() {

        /** Accedo al canale multicast */
        MulticastSocket ms = null;
        InetSocketAddress isa = null;
        NetworkInterface ni = null;
        byte[] buf = new byte[1024];
        try {
            isa = new InetSocketAddress(InetAddress.getByName(ip), port);
            ms = new MulticastSocket(port);
            ni = setInterface(ms);
            ms.joinGroup(isa, ni);

            /** Resto in attesa di nuovi messaggi dal server */
            while (!Thread.currentThread().isInterrupted()) {
                DatagramPacket dp = new DatagramPacket(buf, buf.length);
                ms.receive(dp);      
                String msg = new String(dp.getData(), 0, dp.getLength());
                this.wc.addNotify(msg);
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
