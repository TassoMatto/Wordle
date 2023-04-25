package Server;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.rmi.AlreadyBoundException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import General.ReadConfigFile;
import General.Utils;
import Interfaces.AuthenticationInterface;

/**
 * 
 * @fun                 WordleServer
 * @brief               Classe che gestisce le richieste provenienti dai client
 * 
 */
public class WordleServer implements Runnable {

    /** Variabili globali */
    public static final String fileConfig = "config.txt";
    public static final String backupFile = "users.json";
    public static final int PORT_TCP = 9999;
    public static final int RMI_PORT = 6789;
    public static final long timegameDefault = 60;
    public static final String SocialNetworkIP_DEFAULT = "228.5.6.7";
    public static final int SocialNetworkPORT_DEFAULT = 7000;

    /** Attributi classe */
    private int listeningPort;
    private long timegame;
    private String SocialNetworkIP;
    private int SocialNetworkPORT;
    private UsersDatabase users;
    private Authentication auth;
    private Logger log;
    private Registry reg;

    /**
     * 
     * @class                   runRMI
     * @brief                   Avvio RMI per la gestione delle notifiche riguardanti la classifica agli utenti
     * @throws                  RemoteException-AlreadyBoundException
     * 
     */
    private void runRMI() {

        /** Preparo l'interfaccia per le richieste dei client */
        this.auth = new Authentication(users);
        try {
            AuthenticationInterface ai = (AuthenticationInterface) UnicastRemoteObject.exportObject(auth, 0);
            reg = LocateRegistry.createRegistry(RMI_PORT);
            Registry r = LocateRegistry.getRegistry(RMI_PORT);
            r.bind("//localhost/WordleServer", ai);
        } catch (RemoteException | AlreadyBoundException e) {
            e.printStackTrace();
            return;
        }
    }

    private void stopRMI() {

        /** Preparo l'interfaccia per le richieste dei client */
        try {
            this.reg.unbind("//localhost/WordleServer");
            UnicastRemoteObject.unexportObject(reg, true);
        } catch (RemoteException e) {
            e.printStackTrace();
            return;
        } catch (NotBoundException e) {
            e.printStackTrace();
        }
    }

    /**
     * 
     * @fun                                     WordleServer
     * @brief                                   Metodo costruttore
     * @param configFile                        Pathname del file di configurazione
     * @param backupFile                        Pathname del file di backup JSON
     * @throws FileNotFoundException
     * 
     */
    public WordleServer(String configFile, String backupFile) throws FileNotFoundException {

        /** Controllo argomenti */
        if(configFile.equals("") || backupFile.equals("")) throw new IllegalArgumentException();

        /** Settaggio parametri server */
        String save;
        String[][] paramSettings;
        try {
            paramSettings = ReadConfigFile.readFileConfig(configFile, "ipSocialNetwork", "portSocialNetwork", "listenPort", "timegame");
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        try {
            this.listeningPort = ((save = Utils.filterParam(paramSettings, "listenPort")).equals("")) ? PORT_TCP : Integer.parseInt(save);
            this.SocialNetworkIP = ((save = Utils.filterParam(paramSettings, "ipSocialNetwork")).equals("")) ? SocialNetworkIP_DEFAULT : save;
            this.SocialNetworkPORT = ((save = Utils.filterParam(paramSettings, "portSocialNetwork")).equals("")) ? SocialNetworkPORT_DEFAULT : Integer.parseInt(save);
            this.timegame = ((save = Utils.filterParam(paramSettings, "timegame")).equals("")) ? timegameDefault : Integer.parseInt(save);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        
        /** Avvio logfile */
        log = Logger.getLogger("WordleGameServer");
        FileHandler fh;
        try {
            fh = new FileHandler("./server.log");
            log.addHandler(fh);
            fh.setLevel(Level.ALL);
            log.setLevel(Level.ALL);
            SimpleFormatter simpleFormatter = new SimpleFormatter();
            fh.setFormatter(simpleFormatter);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        /** Costruisco il Database */
        users = new UsersDatabase(backupFile, "words.txt", timegame, log);

    }

    /**
     * 
     * @fun                     run
     * @brief                   Avvia l'esecuzione generale del server
     * 
     */
    @Override
    public void run() {
        
        /** Implemento servizio RMI per login e registrazione di un utente */
        runRMI();

        /** Attendo richieste di accept */
        ExecutorService pool = Executors.newCachedThreadPool();
        log.config(Thread.currentThread().getName() + " Server avviato correttamente\n");
        LinkedList<Socket> clients = new LinkedList<>();
        try (ServerSocket ss = new ServerSocket(listeningPort)) {
            ss.setSoTimeout(2000);
            while (!Thread.interrupted()) {
                try {
                    Socket c;
                    c = ss.accept();
                    clients.add(c);
                    log.info(Thread.currentThread().getName() + " Stabilita connessione con nuovo client\n");
                    pool.execute(new ClientRequest(users, c, this.SocialNetworkIP, this.SocialNetworkPORT, log));
                } catch (SocketTimeoutException e) {
                    Iterator<Socket> i = clients.iterator();
                    while (i.hasNext()) {
                        Socket c = i.next();
                        if(c.isClosed()) i.remove();
                    }
                    continue;
                }
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            return;
        } finally {
            pool.shutdown();                        
            try {
                Iterator<Socket> i = clients.iterator();
                while (i.hasNext()) {
                    Socket c = i.next();
                    if(!c.isClosed()) c.close();
                }
                System.out.println(pool.awaitTermination(5, TimeUnit.SECONDS));
                if(!pool.isTerminated()) pool.shutdownNow();
                System.out.println(pool.awaitTermination(5, TimeUnit.SECONDS));
                this.users.stopDatabase();  
                stopRMI();
                System.exit(0);
            } catch (InterruptedException | IOException e) {
                return;
            }
            
        }
        

    }

}