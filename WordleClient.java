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

    /** Variabili globali */
    public static final String END_GAME_MSG = "timeoutGameover";

    /** Attributi oggetto */
    private String serverIP;
    private int serverPORT;
    private int rmiPORT;
    private String socialNetworkIP;
    private int socialNetworkPORT;
    private String username;
    private String password;
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
     * @fun                             sendMsg
     * @brief                           Manda un messaggio TCP al server
     * @param dos                       Stream di output
     * @param msg                       Messaggio da inviare
     * @throws NullPointerException
     * 
     */
    private void sendMessage(DataOutputStream dos, String msg) {

        /** Controllo argomenti */
        if((dos == null) || (msg == null)) throw new NullPointerException();

        /** Invio la dimensione e il messaggio */
        int reqDim = msg.length();
        try {
            dos.writeInt(reqDim);
            dos.write(msg.getBytes(), 0, reqDim);
        } catch (IOException e) {
            System.err.println("<< Connessione con il server interrotta >>\n");
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
    private String receiveMessage(DataInputStream dis) {

        /** Controllo argomento */
        if(dis == null) throw new NullPointerException();

        /** Resto in attesa di ricevere un messaggio dal server */
        int reqDim;
        byte[] reqByte;
        try {
            reqDim = dis.readInt();  
            reqByte = dis.readNBytes(reqDim);
        } catch (EOFException eof) {
            System.err.println("<< Connessione con il server interrotta >>\n");
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

        /** Invio le credenziali al server */
        sendMessage(dos, username);
        sendMessage(dos, password);
    }

    /**
     *
     * @fun                             requestsToServer
     * @brief                           Gestisce le richieste che il client vuole richiedere al server di gioco
     * @param inputKB                   Input da tastiera delle richieste da fare
     * @param server                    Socket di comunicazione TCP con il server
     * @throws NullPointerException-NumberFormatException-IOException
     * 
     */
    private void requestsToServer(BufferedReader inputKB, Socket server) throws NumberFormatException, IOException {
        
        /** Controllo argomenti */
        if((inputKB == null) || (server == null)) throw new NullPointerException();

        /** Inizio a comunicare con il server */
        try (DataOutputStream dos = new DataOutputStream(server.getOutputStream()); DataInputStream dis = new DataInputStream(server.getInputStream());) {

            /** Prima di procedere, l'utente deve effettuare il login */
            while(true) {
                System.out.println("\n\n<< Inserire credenziali di accesso >>\n");
                System.out.print("<< Username >> ->> ");
                this.username = inputKB.readLine();
                System.out.print("<< Password >> ->> ");
                this.password = inputKB.readLine();
                sendCred(dos);
                int code = dis.readInt();
                if(code == 0) {
                    System.out.println("\n\n<< Accesso effettuato >>\n\n");
                    this.registerClientAlertService();
                    Thread.sleep(1000);
                    break;
                } else if(code == 1) System.out.println("\n\n<< Utente non registrato - Riprovare >>\n\n");
                else if(code == 2) System.out.println("\n\n<< Username o password errati - Riprovare >>\n\n");
                else if(code == 3) System.out.println("\n\n<< Utente già loggato - Riprovare con un altro account o  disconnettersi >>\n\n");
                else if(code == -1) System.out.println("\n\n<< Errore generico - Riprovare >>\n\n");
            }

            /** Se il login ha avuto successo, allora procedo a mostrare le operazioni disponibili */
            int res;
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
                int opt = Integer.parseInt(inputKB.readLine());

                /** Richieste */
                switch(opt) {
                
                    /** Richiedo al server di partecipare al gioco corrente */
                    case 1:
                        sendMessage(dos, "play");
                        res = dis.readInt();
                        switch(res) {

                            /** Errore - Login non effettuato */
                            case 1:
                                System.out.println("<< Utente non ha effettuato l'accesso al server >>");
                            break;

                            /** Utente ha vinto o ha finito i tentativi di gioco */
                            case 2:
                                System.out.println("<< Hai già vinto/finito i tentativi di gioco -- Attendere la fine >>");
                            break;

                            /** Utente pronto a giocare */
                            default:
                                System.out.println("<< Sei pronto a giocare >>");
                                play = true;

                        }
                    break;
                        
                    /** Tentativo di indovinare */
                    case 2:
                        if(play) {  // Solo se ho partecipato al gioco posso inviare una parola al server

                            sendMessage(dos, "gw");
                            //System.out.print("\033[H\033[2J");  
                            System.out.flush();
                            System.out.print("<< Inserisci una parola di 10 lettere >> -->> ");
                            String gw = inputKB.readLine();
                            sendMessage(dos, gw);
                            String tips = receiveMessage(dis);
                            int share;

                            /** Una volta inviata la parola, valuto la risposta */
                            switch(tips) {
                                
                                /** Caso in cui la parola non è presente nel dizionario */
                                case "error":
                                    System.out.println("<< Attenzione - Parola non presente in dizionario >>");
                                    System.out.println("<< Tentativo non valido >>\n");
                                break;

                                /** Caso in cui ho fatto troppi tentativi */
                                case "maxAtt":
                                    System.out.println("<< Numero di tentativi massimo raggiunti >>");
                                    System.out.println("Complimenti - Hai indovinato la parola segreta!");
                                    System.out.print("Desideri condividere i suggerimenti online? \n(1)Si (0)No _>");
                                    share = Integer.parseInt(inputKB.readLine());
                                    if(share == 1) {
                                        sendMessage(dos, "share");
                                        if(dis.readInt() == 0) System.out.println("Suggerimenti pubblicati");
                                        else System.out.println("Impossibile pubblicare i suggerimenti");
                                    }
                                break;
                                
                                /** Utente non ha partecipato al gioco */
                                case "notAllow":
                                    System.out.println("<< Prima di inviare una parola devi richiedere di partecipare al gioco >>");
                                break;

                                /** L'utente ha già partecipato al gioco */
                                case "justWin":
                                    System.out.println("<< Hai già partecipato al gioco -- Attendi la fine per partecipare ad un altro >>");
                                break;

                                /** Caso dove l'utente ha vinto il gioco oppure è scaduto il tempo */
                                default:
                                    if(!tips.contains("+") && !tips.contains("?") && !tips.contains("X")) {
                                        if(tips.contains("win_")) {
                                            play = false;
                                            System.out.println("Complimenti - Hai indovinato la parola segreta!");
                                            System.out.print("Desideri condividere i suggerimenti online? \n(1)Si (0)No _>");
                                            share = Integer.parseInt(inputKB.readLine());
                                            if(share == 1) {
                                                sendMessage(dos, "share");
                                                if(dis.readInt() == 0) System.out.println("Suggerimenti pubblicati");
                                                else System.out.println("Impossibile pubblicare i suggerimenti");
                                            }
                                        }
                                        else System.out.println("<< Tempo di gioco scaduto - La parola segreta era: " + tips.substring(8) + " >>");
                                    } else System.out.println("<< SUGGERIMENTO: " + tips + " >>");
                            }
                        }
                    break;

                    /** Richiesta al server di generare le statistiche */
                    case 3:
                        sendMessage(dos, "statistics");
                        String statistics = receiveMessage(dis);
                        System.out.println("\n" + statistics);
                    break;

                    /** Stampo i post dei game degli altri utenti */
                    case 4:
                        synchronized(this.serverNotice) {
                            Iterator<String> i = this.serverNotice.iterator();
                            while (i.hasNext()) {
                                String s = i.next();
                                System.out.println(s);
                            }
                            this.serverNotice.clear(); 
                        }
                    break;

                    /** Stampo la classifica dei primi 3 del gioco */
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
                        sendMessage(dos, "logout");
                        res = dis.readInt();
                        if(res != 0) System.err.println("Errore durante logout");
                        else { System.out.println("Utente esce dal gioco"); return; }
                    break;
                }
                Thread.sleep(1000);
            }
        } catch (Exception e) {
            e.printStackTrace();
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
            this.serverNotice = new LinkedList<>();
            this.podium = new LinkedList<>();
            this.socialNetworkListener = new Thread(new SocialNetworkNotify(this, this.socialNetworkIP, this.socialNetworkPORT));
            this.socialNetworkListener.start();
        
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return;
        }

    } 

    /**
     * 
     * @fun                     run
     * @brief                   Gestisce la comunicazione client - server
     * 
     */
    @Override
    public void run() {
        
        /** Avvio il client */
        try (
            BufferedReader inputKB = new BufferedReader(new InputStreamReader(System.in));
        ){
            /** RMI per effettuare la registrazione dell'utente */
            AuthenticationInterface ai;
            Registry r = LocateRegistry.getRegistry(this.rmiPORT);
            ai = (AuthenticationInterface) r.lookup("//localhost/WordleServer");
            
            /** Chiedo all'utente se vuole registrarsi o no */
            System.out.println("\t\t\t<< WORDLE >>");
            System.out.println("<< Desideri registrarti al server di gioco? >>");
            System.out.println("<< (1) Registrazione - (0) Login >>");
            System.out.print(">>");
            int opt = Integer.parseInt(inputKB.readLine());

            /** Valuto scelta utente */
            while(opt == 1) {
                
                /** Registro l'utente sul server */
                System.out.println("\n\n< Inserire credenziali per la registrazione >\n");
                System.out.print("< Username > ->> ");
                String username = inputKB.readLine();
                System.out.print("< Password > ->> ");
                String password = inputKB.readLine();
                int code = ai.register(username, password);
                if(code == 0) { System.out.println("\n\n<< Registrazione dell'utente effettuata >>\n\n"); break; }
                else if(code == 1) System.out.println("\n\n<< Registrazione fallita - Utente già presente nel database di gioco >>\n\n");
                else System.out.println("\n\n<< Registrazione fallita - Errore generico >>\n\n");

            }
            
            /** Effettuare accesso al server */
            try (Socket server = new Socket(this.serverIP, this.serverPORT)){
                requestsToServer(inputKB, server);
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
    }

    /**
     * 
     * @fun                             ServerAlert
     * @brief                           Gestisce le comunicazioni del server per le prime tre posizioni della classifica
     * @param update                    Podio aggiornato
     * @throws NullPointerException
     * 
     */
    @Override
    public void ServerAlert(LinkedList<String> update) throws RemoteException {

        /** Controllo argomenti */
        if(update == null) throw new NullPointerException();

        /** Aggiorno la classifica locale dell'utente */
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

    /** 
     * 
     * @fun                         addNotify
     * @brief                       Aggiorna la lista delle notifiche del server
     * 
     */
    public void addNotify(String notice) {

        /** Controllo argomento */
        if(notice == null) throw new NullPointerException();

        /** Aggiungo la notifiche del social network */
        synchronized(this.serverNotice) {
            this.serverNotice.add(notice);
        }
    }
}
