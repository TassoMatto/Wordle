package Server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;

public class ClientRequest implements Runnable {

    /** Attributi */
    private Socket client;
    private String usernameC;
    private String passwordC;
    private UsersDatabase us;
    private String ipSocialNetwork;
    private int portSocialNetwork;

    public ClientRequest(UsersDatabase us, Socket client, String ipSocialNetowork, int portSocialNetwork) {
        this.us = us;
        this.client = client;
        this.ipSocialNetwork = ipSocialNetowork;
        this.portSocialNetwork = portSocialNetwork;
    }

    private void shareUserAttempts() {

        ArrayList<String> att = this.us.giveUserAttempt(usernameC, passwordC);
        if(att == null) return;
        try (DatagramSocket ds = new DatagramSocket()) {
            InetAddress ia = InetAddress.getByName(this.ipSocialNetwork);
            String s = att.toString();
            DatagramPacket dp = new DatagramPacket(s.getBytes(), s.length(), ia, this.portSocialNetwork);
            ds.send(dp);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

    }
    
    private void getLoginCred(DataInputStream dis) {

        int dim;
        byte[] msg = {};

        try {
            dim = dis.readInt();
            dis.read(msg, 0, dim);
            this.usernameC = msg.toString();
            dim = dis.readInt();
            dis.read(msg, 0, dim);
            this.passwordC = msg.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

    }

    private String getMsg(DataInputStream dis) {
        int reqDim;
        byte[] reqByte = {};
        try {
            reqDim = dis.readInt();  
            dis.read(reqByte, 0, reqDim);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        
        return reqByte.toString();
    }

    private void sendMsg(DataOutputStream dos, String msg) {
        int reqDim;

        reqDim = msg.length();
        try {
            dos.writeInt(reqDim);
            dos.write(msg.getBytes(), 0, reqDim);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
    }

    @Override
    public void run() {

        try (
            DataInputStream dis = new DataInputStream(client.getInputStream());
            DataOutputStream dos = new DataOutputStream(client.getOutputStream());
        ) {
            getLoginCred(dis);
            while (!Thread.currentThread().isInterrupted()) {
                switch(getMsg(dis)) {
                
                    /** Richiesta del client di giocare all'ultimo gioco */
                    case "play":
                        this.us.playGame(usernameC, passwordC);
                        dos.writeInt(0);                        // Ack
                    break;
                    
                    case "gw":
                        String sw = getMsg(dis);
                        String resend = this.us.sendGuessedWord(usernameC, passwordC, sw);
                        sendMsg(dos, resend);
                    break;
    
                    case "statistics":
                        String statistics = this.us.userStatistics(usernameC, passwordC);
                        sendMsg(dos, statistics);
                    break;
    
                    case "share":
                        shareUserAttempts();
                    break;
                
                    case "logout":
                        this.us.logoutUser(usernameC, passwordC);
                        dos.writeInt(0);
                    break;
    
                    default:
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
       
        

    }
    
}
