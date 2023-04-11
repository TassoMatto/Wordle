package Server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
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
        StringBuilder sb = new StringBuilder();
        if(att == null) return;
        System.out.println(ipSocialNetwork + " " + portSocialNetwork);
        try (DatagramSocket ds = new DatagramSocket()) {
            InetAddress ia = InetAddress.getByName(this.ipSocialNetwork);
            sb.append(att.toString());
            String s = sb.toString();
            DatagramPacket dp = new DatagramPacket(s.getBytes(), s.length(), ia, this.portSocialNetwork);
            ds.send(dp);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

    }
    
    private void getLoginCred(DataInputStream dis) {

        int dim;
        byte[] msg;

        try {
            dim = dis.readInt();
            msg = dis.readNBytes(dim);
            this.usernameC = new String(msg, 0, dim);
            dim = dis.readInt();
            msg = dis.readNBytes(dim);
            this.passwordC = new String(msg, 0, dim);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

    }

    private String getMsg(DataInputStream dis) {
        int reqDim;
        byte[] reqByte;
        try {
            reqDim = dis.readInt();  
            reqByte = dis.readNBytes(reqDim);
        } catch (EOFException eof) {
            System.out.println("Client disconnesso");
            return "stop";
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        
        return new String(reqByte, 0, reqDim);
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
            System.out.println("Credenziali acquisite . " + usernameC + " "+ passwordC);
            while (!Thread.currentThread().isInterrupted()) {
                switch(getMsg(dis)) {
                
                    /** Richiesta del client di giocare all'ultimo gioco */
                    case "play":
                        System.out.println("Voglio joca'");
                        System.out.println("Funziona? " + this.us.playGame(usernameC, passwordC));
                        dos.writeInt(0);                        // Ack
                    break;
                    
                    case "gw":
                        String sw = getMsg(dis);
                        String resend = this.us.sendGuessedWord(usernameC, passwordC, sw);
                        if(resend == null) sendMsg(dos, "error");
                        else sendMsg(dos, resend);
                    break;
    
                    case "statistics":
                        String statistics = this.us.userStatistics(usernameC, passwordC);
                        sendMsg(dos, statistics);
                    break;
    
                    case "share":
                        System.out.println("condivido");
                        shareUserAttempts();
                        dos.writeInt(0);
                    break;
                
                    case "logout":
                        System.out.println("Faccio il cazzo di logout");
                        this.us.logoutUser(usernameC, passwordC);
                        dos.writeInt(0);
                    break;

                    default:
                        return;
                }
            }
        } 
        catch (SocketException se) {
            //GESTIRE CHIUSURA IMPROVVISA CLIENT
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
       
        

    }
    
}
