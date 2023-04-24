package Server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.logging.Logger;

import General.Utils;

public class ClientRequest implements Runnable {

    /** Attributi */
    private Socket client;
    private String usernameC;
    private String passwordC;
    private UsersDatabase us;
    private String ipSocialNetwork;
    private int portSocialNetwork;
    private Logger log;

    public ClientRequest(UsersDatabase us, Socket client, String ipSocialNetowork, int portSocialNetwork, Logger log) {
        this.us = us;
        this.client = client;
        this.ipSocialNetwork = ipSocialNetowork;
        this.portSocialNetwork = portSocialNetwork;
        this.log = log;
    }

    private void shareUserAttempts() {

        ArrayList<String> att = this.us.giveUserAttempt(usernameC, passwordC);
        StringBuilder sb = new StringBuilder();
        if(att == null) return;
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

    @Override
    public void run() {

        try (
            DataInputStream dis = new DataInputStream(client.getInputStream());
            DataOutputStream dos = new DataOutputStream(client.getOutputStream());
        ) {

            /** Utente richiede accesso al database di gioco */
            this.log.info(Thread.currentThread().getName() + " " + "In attesa delle credenziali di accesso al server");
            while(true) {
                getLoginCred(dis);
                int code = this.us.loginUser(usernameC, passwordC);
                dos.writeInt(code);
                if(code == 0) break;
            }
            this.log.info(Thread.currentThread().getName() + " Utente " + this.usernameC + " entrato nel server");

            while (!Thread.currentThread().isInterrupted()) {
                switch(Utils.receiveMessage(dis)) {
                
                    /** Richiesta del client di giocare all'ultimo gioco */
                    case "play":
                        this.log.info(Thread.currentThread().getName() + " " + this.usernameC + " - Richiesta di partecipare al gioco");
                        int res = this.us.playGame(usernameC, passwordC);
                        dos.writeInt(res);                        
                        switch (res) {
                            case 0:
                                this.log.fine(Thread.currentThread().getName() + " Utente " + this.usernameC + " Ora sta partecipando al gioco ");  
                            break;
                        
                            case 1:
                                this.log.info(Thread.currentThread().getName() + " Utente " + this.usernameC + " già partecipe del gioco");
                            break;

                            default:
                                this.log.warning(Thread.currentThread().getName() + " Utente " + this.usernameC + " Errore - Non può partecipare al gioco ");  
                            break;
                        }
                    break;
                    
                    case "gw":
                        this.log.info(Thread.currentThread().getName() + " " + this.usernameC + " - Vuole indovinare la parola segreta");
                        String sw = Utils.receiveMessage(dis);
                        String resend = this.us.sendGuessedWord(usernameC, passwordC, sw);
                        if(resend == null) {
                            Utils.sendMessage(dos, "error");
                            this.log.warning(Thread.currentThread().getName() + " " + this.usernameC + " - Errore durante confronto parola");
                        }
                        else {
                            Utils.sendMessage(dos, resend);
                            this.log.info(Thread.currentThread().getName() + " " + this.usernameC + " Risposta inviata al client");
                        }
                    break;
    
                    case "statistics":
                        this.log.info(Thread.currentThread().getName() + " " + this.usernameC + " Richiesta di elaborazione delle proprie statistiche");
                        String statistics = this.us.userStatistics(usernameC, passwordC);
                        Utils.sendMessage(dos, statistics);
                    break;
    
                    case "share":
                        this.log.info(Thread.currentThread().getName() + " " + this.usernameC + " Richiesta di condividere i risultati di gioco");
                        shareUserAttempts();
                        dos.writeInt(0);
                    break;
                
                    case "logout":
                        this.log.warning(Thread.currentThread().getName() + " " + this.usernameC + " Disconnessione dal server");
                        this.us.logoutUser(usernameC, passwordC);
                        dos.writeInt(0);
                        this.log.fine(Thread.currentThread().getName() + " " + this.usernameC + " Disconnesso dal server di gioco");
                    break;

                    default:
                        this.log.warning(Thread.currentThread().getName() + " " + this.usernameC + " Richiesta non disponibile - Errore");
                        return;
                }
            }
        } 
        catch (SocketException se) {
            this.log.warning(Thread.currentThread().getName() + " " + this.usernameC + " Perdita di connessione con il client - Fase di logout automatico");
            this.us.logoutUser(usernameC, passwordC);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
       
        

    }
    
}
