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
    private String ipServer;
    private int portServer;
    private int RMI_PORT;
    private String socialIp;
    private int socialPORT;
    private String username;
    private String password;
    private Thread thisT;
    private Boolean stopGame;
    private String lastWord;

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
            Registry r = LocateRegistry.getRegistry(this.RMI_PORT);
            ai = (AuthenticationInterface) r.lookup("//localhost/WordleServer");
            System.out.print("Username: > ");
            String usern = br.readLine();
            System.out.print("Password: > ");
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
            Registry r = LocateRegistry.getRegistry(RMI_PORT);
            ai = (AuthenticationInterface) r.lookup("//localhost/WordleServer");
            System.out.print("Username: > ");
            String usern = br.readLine();
            System.out.print("Password: > ");
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
            Registry r = LocateRegistry.getRegistry(RMI_PORT);
            ai = (AuthenticationInterface) r.lookup("//localhost/WordleServer");
            ServerNotify sn = (ServerNotify) UnicastRemoteObject.exportObject((ServerNotify) this, 0);
            this.thisT = Thread.currentThread();
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
            Registry r1 = LocateRegistry.getRegistry(RMI_PORT);
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

        System.out.println(username + " " + password);
        sendMsg(dos, username);
        sendMsg(dos, password);
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
            while (true) {
                System.out.print("\033[H\033[2J");  
                System.out.flush();
                System.out.println("1) Partecipa al gioco");
                System.out.println("2) Invia una parola da indovinare");
                System.out.println("3) Calcolo statistiche utente");
                System.out.println("4) Condividi il risultato di gioco con gli altri");
                System.out.println("5) Mostra notifiche del server di gioco");
                System.out.println("6) Mostrami prime 3 posizioni della classifica");
                System.out.println("7) Logout dal server");
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
                    break;
                        
                    /** Tentativo di indovinare */
                    case 2:
                        sendMsg(dos, "gw");
                        System.out.print("\033[H\033[2J");  
                        System.out.flush();
                        System.out.print("Inserisci una parola di 10 lettere: ");
                        String gw = br.readLine();
                        sendMsg(dos, gw);
                        String tips = getMsg(dis);

                        if(tips.equals("maxAtt")) {
                            if(isEnd()) resetEndGame();
                            else System.out.println("Numero di tentativi massimo raggiunti!");
                        } else System.out.println(tips);
                    break;

                    case 3:
                        sendMsg(dos, "statistics");
                        String statistics = getMsg(dis);
                        System.out.println("\n" + statistics);
                    break;
    
                    /** Logout client dal server */
                    case 7:
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
            
            this.socialIp = filterParam(param, "ipSocialNetwork");
            this.socialPORT = Integer.parseInt(filterParam(param, "portSocialNetwork"));
            this.RMI_PORT = Integer.parseInt(filterParam(param, "portRMI"));
            this.ipServer = filterParam(param, "serverIP");
            this.portServer = Integer.parseInt(filterParam(param, "listenPort"));
            this.stopGame = false;
        
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
            Socket serverSocket = new Socket(ipServer, portServer);
            )
        {
            System.out.print("\033[H\033[2J");  
            System.out.flush(); 
            System.out.println("\t\t\t[BENVENUTO SU WORDLE]\n\n");

            /** Prima chiedo se l'utente vuole registrarsi, poi effettuo login */
            System.out.println("> Ci si vuole registrare al server di gioco?");
            System.out.println("(1) Si - (0) No");
            System.out.print("> ");
            Integer opt = Integer.parseInt(br.readLine());
            switch (opt) {
                case 1:
                    System.out.print("\033[H\033[2J");  
                    System.out.flush();
                    if(registerRequest(br)) {
                        System.out.print("\033[H\033[2J");  
                        System.out.flush();
                        System.out.println("Registrazione effettuata - Effettuare login\n\n"); 
                        if((logged = loginRequest(br))) {
                            System.out.print("\033[H\033[2J");  
                            System.out.flush();
                            System.out.println("Accesso al server di gioco effettuato");
                        }
                    } else {
                        System.out.println("Errore nella fase di registrazione");
                    }
                break;

                case 0:
                    System.out.print("\033[H\033[2J");  
                    System.out.flush();
                    if((logged = loginRequest(br))) {
                        System.out.print("\033[H\033[2J");  
                        System.out.flush();
                        System.out.println("Accesso al server di gioco effettuato");
                    } else {
                        System.out.println("Errore nella fase di registrazione");
                        return;
                    }
                break;
            }

            Thread.sleep(2000);
            if(logged) {
                registerClientAlertService();
                requestsToServer(br, serverSocket);
                System.out.print("\033[H\033[2J");  
                System.out.flush();
                System.out.println("Grazie di giocato a Wordle: un gioco di parole 3.0\n\n");
            }
            
            

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void ServerAlert() throws RemoteException {
        
    }

    @Override
    public synchronized void EndGame(String oldWord) throws RemoteException {
        synchronized(this) {
            stopGame = true;
            this.lastWord = oldWord;
        }
    }

    private boolean isEnd() {
        synchronized (this) {
            return stopGame;
        }
    }

    private void resetEndGame() {
        int i = -1;
        synchronized(this) {
            this.stopGame = false;
        }
    
        while(++i < 5) {
            System.out.print("\033[H\033[2J");  
            System.out.flush();
            System.out.println("\t\t\t[SESSIONE DI GIOCO TERMINATA]\n\n");
            System.out.print("La parola segreta era: ");
            System.out.println(this.lastWord);
            System.out.print("\nPotrai tornare a giocare tra " + (5 - i) + " sec");
            try {
                Thread.sleep(400);
                System.out.print(".");
                Thread.sleep(400);
                System.out.print(".");
                Thread.sleep(400);
                System.out.print(".");
            } catch (InterruptedException e) {
                e.printStackTrace();
                return;
            }
        }
        
    }


}
