package Client;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.net.Socket;
import java.nio.file.Path;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Iterator;
import java.util.LinkedList;
import Interfaces.AuthenticationInterface;
import Interfaces.ServerNotify;
import Server.ReadConfigFile;

/**
 * 
 * @class                   WordleClient
 * @brief                   Gestisce le operazioni che un utente può effettuare sul server di gioco, gestisce le notifiche di alert
 *                          provenienti dal server, condivide informazioni agli altri utenti sul social network
 * @author                  Simone Tassotti
 * @date                    28/03/2023
 * 
 */
public class WordleClient implements Runnable, ServerNotify, Serializable {

    /** Attributi oggetto */
    private String serverIP;
    private int serverPORT;
    private int rmiPORT;
    private String socialNetworkIP;
    private int socialNetworkPORT;
    private String username;
    private String password;
    private Boolean stopGame;
    private String lastWord;
    private LinkedList<String> serverNotice;
    private Thread socialNetworkListener;
    private LinkedList<String> podium;

                                        /********** METODI PRIVATI **********/

    /**
     * 
     * @fun                     findParam
     * @brief                   Trova per il campo interessato il valore corrispondente in formato String
     * @param array             Matrice che contiene le coppie (parametro, valore)
     * @param param             Parametro che voglio filtrare
     * @return                  Ritorna il valore del parametro filtrato, "" se non esiste match con il parametro
     * 
     */
    private String filterParam(String[][] array, String param) {
        for (int i = 0; i < array.length; i++) {
            if(array[i][0].equals(param)) return array[i][1];
        }
        return "";
    }

    /**
     * 
     * @fun                     registerRequest
     * @breif                   Richiesta di registrare un utente sul server
     * @param br                Buffer per l'input da tastiera delle credenziali
     * @return                  true in caso di successo, false altrimenti
     *
     */
    private boolean registerRequest(BufferedReader br) {

        /** Controllo argomenti */
        if(br == null) throw new IllegalArgumentException();

        /** Recupero il riferimenti RMI del server */
        AuthenticationInterface ai;
        try {
            Registry r = LocateRegistry.getRegistry(this.rmiPORT);
            ai = (AuthenticationInterface) r.lookup("//localhost/WordleServer");
            System.out.print("[Username] >");
            String usern = br.readLine();
            System.out.print("[Password] >");
            String passw = br.readLine();
            return ai.register(usern, passw);
        } catch (NotBoundException | IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 
     * @fun                     loginRequest
     * @brief                   Richiesta di login al server
     * @param br                Buffer per l'input da tastiera delle credenziali
     * @return                  true in caso di successo, false altrimenti
     * 
     */
    private boolean loginRequest(BufferedReader br) {

        /** Controllo argomenti */
        if(br == null) throw new IllegalArgumentException();

        /** Recupero il riferimenti RMI del server */
        AuthenticationInterface ai;
        try {
            Registry r = LocateRegistry.getRegistry(this.rmiPORT);
            ai = (AuthenticationInterface) r.lookup("//localhost/WordleServer");
            System.out.print("[Username] >");
            String usern = br.readLine();
            System.out.print("[Password] >");
            String passw = br.readLine();
            boolean res = ai.login(usern, passw) != -1;
            if(res) {
                this.username = usern;
                this.password = passw;
            }
            return res;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 
     * @fun                 registerClientAlertService
     * @brief               Registra il client al servizio di notifiche del server
     * 
     */
    private void registerClientAlertService() {

        /** Comunico con RMI del server */
        AuthenticationInterface ai;
        try {
            Registry r = LocateRegistry.getRegistry(this.rmiPORT);
            ai = (AuthenticationInterface) r.lookup("//localhost/WordleServer");
            ServerNotify sn = (ServerNotify) UnicastRemoteObject.exportObject((ServerNotify) this, 0);
            ai.registerClient(sn, this.username, this.password);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
    }

    /**
     * 
     * @fun                 registerClientAlertService
     * @brief               Registra il client al servizio di notifiche del server
     * 
     */
    private void unregisterClientAlertService() {

        /** Comunico con RMI del server */
        AuthenticationInterface ai;
        try {            
            Registry r1 = LocateRegistry.getRegistry(this.rmiPORT);
            ai = (AuthenticationInterface) r1.lookup("//localhost/WordleServer");            
            ai.unregisterClient(username, password);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
    }

    /**
     * 
     * @fun                             sendMsg
     * @brief                           Manda un messaggio TCP al server
     * @param dos                       Stream di output
     * @param msg                       Messaggio da inviare
     * @throws NullPointerException
     * 
     */
    private void sendMsg(DataOutputStream dos, String msg) {

        /** Controllo argomenti */
        if((dos == null) || (msg == null)) throw new NullPointerException();

        /** Invio la dimensione e il messaggio */
        int reqDim = msg.length();
        try {
            dos.writeInt(reqDim);
            dos.write(msg.getBytes(), 0, reqDim);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
    }

    /**
     * 
     * @fun                             getMsg
     * @brief                           Ricevo messaggio TCP dal server
     * @param dis                       InputStream
     * @throws NullPointerException
     * @return                          Il messaggio ricevuto, null in caso di errore
     */
    private String getMsg(DataInputStream dis) {

        /** Controllo argomento */
        if(dis == null) throw new NullPointerException();

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

    /**
     * 
     * @fun                             sendCred
     * @brief                           Invio credenziali di accesso al server
     * @param dos                       Stream di output
     * @throws NullPointerException     
     * 
     */
    private void sendCred(DataOutputStream dos) {

        /** Controllo argomento */
        if(dos == null) throw new NullPointerException();

        sendMsg(dos, username);
        sendMsg(dos, password);
    }

    /**
     * 
     * @fun                             isEnd
     * @brief                           Controlla se il server ha determinato la fine del gioco
     * @return                          (true) se il gioco è terminato, (false) altrimenti
     */
    private boolean isEnd() {
        synchronized(this.lastWord) {
            return this.stopGame;
        }
    }

    /**
     *
     * @fun                             requestsToServer
     * @brief                           Gestisce le richieste che il client vuole richiedere al server di gioco
     * @param br                        Input da tastiera delle richieste da fare
     * @param server                    Socket di comunicazione TCP con il server
     * @throws NullPointerException
     * 
     */
    private void requestsToServer(BufferedReader br, Socket server) throws NumberFormatException, IOException {
        
        /** Controllo argomenti */
        if((br == null) || (server == null)) throw new NullPointerException();

        /** Inizio a comunicare con il server */
        try (DataOutputStream dos = new DataOutputStream(server.getOutputStream()); DataInputStream dis = new DataInputStream(server.getInputStream());) {

            int res;
            sendCred(dos);
            boolean play = false;
            while (true) {
                //System.out.print("\033[H\033[2J");  
                System.out.flush();
                System.out.println("1) Partecipa al gioco");
                if(play) System.out.println("2) Invia una parola da indovinare");
                System.out.println("3) Calcolo statistiche utente");
                System.out.println("4) Mostra notifiche del server di gioco");
                System.out.println("5) Mostrami prime 3 posizioni della classifica");
                System.out.println("6) Logout dal server");
                int opt = Integer.parseInt(br.readLine());
                if(isEnd()) { resetEndGame(); continue; }

                /** Richieste */
                switch(opt) {
                
                    /** Richiedo al server di partecipare al gioco corrente */
                    case 1:
                        sendMsg(dos, "play");
                        res = dis.readInt();
                        if(res != 0) {
                            if(isEnd()) resetEndGame();
                            else System.err.println("Errore durante la richiesta di partecipazione al gioco");
                            
                        }
                        else System.out.println("Stai partecipando al gioco"); 
                        play = true;
                    break;
                        
                    /** Tentativo di indovinare */
                    case 2:
                        if(play) {
                            sendMsg(dos, "gw");
                            //System.out.print("\033[H\033[2J");  
                            System.out.flush();
                            System.out.print("Inserisci una parola di 10 lettere: ");
                            String gw = br.readLine();
                            sendMsg(dos, gw);
                            String tips = getMsg(dis);

                            if(tips.equals("error")) System.out.println("Attenzione - Parola non presente in dizionario!");

                            if(tips.equals("maxAtt")) {
                                if(isEnd()) resetEndGame();
                                else {
                                    System.out.println("Numero di tentativi massimo raggiunti!");
                                    System.out.print("Desideri condividere i suggerimenti online? \n(1)Si (0)No _>");
                                    int share = Integer.parseInt(br.readLine());
                                    if(share == 1) {
                                        sendMsg(dos, "share");
                                        if(dis.readInt() == 0) System.out.println("Suggerimenti pubblicati");
                                        else System.out.println("Impossibile pubblicare i suggerimenti");
                                    }
                                }
                            } else if(tips.equals("++++++++++")) {
                                System.out.println("Complimenti - Hai indovinato la parola segreta!");
                                System.out.print("Desideri condividere i suggerimenti online? \n(1)Si (0)No _>");
                                int share = Integer.parseInt(br.readLine());
                                if(share == 1) {
                                    sendMsg(dos, "share");
                                    if(dis.readInt() == 0) System.out.println("Suggerimenti pubblicati");
                                    else System.out.println("Impossibile pubblicare i suggerimenti");
                                }
                            } else {
                                System.out.println(tips);
                            }
                        }
                    break;

                    case 3:
                        sendMsg(dos, "statistics");
                        String statistics = getMsg(dis);
                        System.out.println("\n" + statistics);
                    break;

                    case 4:
                        synchronized(this.serverNotice) {
                            Iterator<String> i = this.serverNotice.iterator();
                            while (i.hasNext()) {
                                String s = i.next();
                                System.out.println(s);
                            }
                            this.serverNotice = null;  
                        }
                    break;

                    case 5:
                        synchronized(this.podium) {
                            Iterator<String> i = this.podium.iterator();
                            while(i.hasNext()) {
                                String s = i.next();
                                System.out.println(s);
                            }
                        }
                    break;
    
                    /** Logout client dal server */
                    case 6:
                        sendMsg(dos, "logout");
                        res = dis.readInt();
                        if(res != 0) System.err.println("Errore durante logout");
                        else { System.out.println("Utente esce dal gioco"); this.unregisterClientAlertService(); return; }
                    break;
                }
                if(isEnd()) { resetEndGame(); continue; }
                else Thread.sleep(1000);
            }
        } catch (Exception e) {
            e.printStackTrace();
            this.unregisterClientAlertService();
        } 
    }

                                        /************************************/

    /**
     * 
     * @fun                                 WordleClient
     * @brief                               Metodo costruttore
     * @param configFile                    File di config da leggere per inizializzare il client
     * @throws IllegalArgumentException
     * 
     */
    public WordleClient(String configFile) {

        /** Controllo argomenti */
        if((configFile == null) || (configFile.equals("")) || (!Path.of(configFile).toFile().exists()) || (!Path.of(configFile).toFile().isFile())) throw new IllegalArgumentException();

        /** Estraggo le informazioni dal file di config */
        try {
            String[][] param = ReadConfigFile.readFileConfig(configFile, "portSocialNetwork", "ipSocialNetwork", "portRMI", "serverIP", "listenPort");
            
            this.socialNetworkIP = filterParam(param, "ipSocialNetwork");
            this.socialNetworkPORT = Integer.parseInt(filterParam(param, "portSocialNetwork"));
            this.rmiPORT = Integer.parseInt(filterParam(param, "portRMI"));
            this.serverIP = filterParam(param, "serverIP");
            this.serverPORT = Integer.parseInt(filterParam(param, "listenPort"));
            this.stopGame = false;
            this.serverNotice = new LinkedList<>();
            this.podium = new LinkedList<>();
            this.lastWord = "";
            this.socialNetworkListener = new Thread(new SocialNetworkNotify(this, this.socialNetworkIP, this.socialNetworkPORT));
            this.socialNetworkListener.start();
        
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return;
        }

    } 

    @Override
    public void run() {

        boolean logged = false;
        
        try (
            BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
            Socket serverSocket = new Socket(this.serverIP, this.serverPORT);
            )
        {
            System.out.print("\033[H\033[2J");  
            System.out.flush(); 
            System.out.println("\t\t\t[BENVENUTO SU WORDLE]\n\n");

            /** Prima chiedo se l'utente vuole registrarsi, poi effettuo login */
            System.out.println("[REGISTRAZIONE(1) - ACCESSO AL GIOCO(0)]");
            System.out.print(">");
            Integer opt = Integer.parseInt(br.readLine());

            /** Valuto la scelta dell'utente */
            while(!logged) {
                switch (opt) {
                    case 1:
                        //System.out.print("\033[H\033[2J");  
                        System.out.flush();
                        if(registerRequest(br)) {
                            //System.out.print("\033[H\033[2J");  
                            System.out.flush();
                            System.out.println("Registrazione effettuata - Effettuare login\n\n"); 
                            if((logged = loginRequest(br))) {
                                //System.out.print("\033[H\033[2J");  
                                System.out.flush();
                                System.out.println("Accesso al server di gioco effettuato");
                            }
                        } else {
                            //System.out.print("\033[H\033[2J");  
                            System.out.flush();
                            System.out.println("Username o password errati!");
                        }
                    break;
    
                    case 0:
                        //System.out.print("\033[H\033[2J");  
                        System.out.flush();
                        if((logged = loginRequest(br))) {
                            //System.out.print("\033[H\033[2J");  
                            System.out.flush();
                            System.out.println("Accesso al server di gioco effettuato");
                        } else {
                            //System.out.print("\033[H\033[2J");  
                            System.out.flush();
                            System.out.println("Username o password errati!");
                        }
                    break;
                }
            }
           

            Thread.sleep(2000);
            if(logged) {
                registerClientAlertService();
                requestsToServer(br, serverSocket);
                //System.out.print("\033[H\033[2J");  
                System.out.flush();
                System.out.println("Grazie di giocato a Wordle: un gioco di parole 3.0\n\n");
            }
            
            

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void ServerAlert(LinkedList<String> update) throws RemoteException {
        synchronized(this.podium) {
            int dim = update.size();
            if(this.podium.size() == 0) {
                while(dim != 0) { this.podium.add(update.removeFirst()); dim--; }
            } else {
                int i = -1;
                while(++i < dim) {
                    if(!this.podium.get(i).equals(update.get(i))) {
                        this.podium.remove(i);
                        this.podium.add(i, update.remove(i));
                    }
                }
            }
        }
    }

    @Override
    public void EndGame(String oldWord) throws RemoteException {
        synchronized(this.lastWord) {
            stopGame = true;
            this.lastWord = oldWord;
        }
    }

    private void resetEndGame() {
        int i = -1;
        synchronized(this.lastWord) {
            this.stopGame = false;
        }
    
        while(++i < 3) {
            System.out.print("\033[H\033[2J");  
            System.out.flush();
            System.out.println("\t\t\t[SESSIONE DI GIOCO TERMINATA]\n\n");
            System.out.print("La parola segreta era: ");
            System.out.println(this.lastWord);
            System.out.print("\nPotrai tornare a giocare tra " + (3 - i) + " sec");
            try {
                Thread.sleep(330);
                System.out.print(".");
                Thread.sleep(330);
                System.out.print(".");
                Thread.sleep(330);
                System.out.print(".");
            } catch (InterruptedException e) {
                e.printStackTrace();
                return;
            }
        }
        
    }

    public void addNotify(String notice) {
        synchronized(this.serverNotice) {
            this.serverNotice.add(notice);
        }
    }
}
