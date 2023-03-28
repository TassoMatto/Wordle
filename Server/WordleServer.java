package Server;

import java.io.FileNotFoundException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.rmi.AlreadyBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import Interfaces.AuthenticationInterface;

public class WordleServer implements Runnable {

    /** Variabili globali */
    public static final String fileConfig = "config.txt";
    public static final String backupFile = "users.json";
    public static final int PORT_TCP = 9999;
    public static final int RMI_PORT = 6789;
    public static final long timegameDefault = 10;
    public static final String SocialNetworkIP_DEFAULT = "228.5.6.7";
    public static final int SocialNetworkPORT_DEFAULT = 7000;

    /** Attributi classe */
    private int listeningPort;
    private long timegame;
    private String SocialNetworkIP;
    private int SocialNetworkPORT;
    private UsersDatabase users;
    private Authentication auth;

    private String findParam(String[][] array, String param) {
        for (int i = 0; i < array.length; i++) {
            if(array[i][0].equals(param)) return array[i][1];
        }
        return "";
    }

    private void runRMI() {
        this.auth = new Authentication(users);
        try {
            AuthenticationInterface ai = (AuthenticationInterface) UnicastRemoteObject.exportObject(auth, 0);
            LocateRegistry.createRegistry(RMI_PORT);
            Registry r = LocateRegistry.getRegistry(RMI_PORT);
            r.bind("//localhost/WordleServer", ai);
        } catch (RemoteException | AlreadyBoundException e) {
            e.printStackTrace();
            return;
        }
    }

    public WordleServer(String configFile, String backupFile) throws FileNotFoundException {

        /** Controllo argomenti */
        if(configFile.equals("") || backupFile.equals("")) throw new IllegalArgumentException();

        /** Settaggio parametri server */
        String save;
        String[][] paramSettings;
        try {
            paramSettings = Bootload.readFileConfig(configFile, "ipSocialNetwork", "portSocialNetwork", "portSocialNetwork", "listenPort");
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        try {
            this.listeningPort = ((save = findParam(paramSettings, "port")).equals("")) ? PORT_TCP : Integer.parseInt(save);
            this.timegame = ((save = findParam(paramSettings, "timegame")).equals("")) ? timegameDefault : Long.parseLong(save);
            this.SocialNetworkIP = ((save = findParam(paramSettings, "socialIP")).equals("")) ? SocialNetworkIP_DEFAULT : save;
            this.SocialNetworkPORT = ((save = findParam(paramSettings, "socialPORT")).equals("")) ? SocialNetworkPORT_DEFAULT : Integer.parseInt(save);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        
        /** Costruisco il Database */
        users = new UsersDatabase(backupFile, "words.txt");

    }

    @Override
    public void run() {
        
        /** Implemento servizio RMI per login e registrazione di un utente */
        runRMI();

        /** Attendo richieste di accept */
        ExecutorService pool = Executors.newCachedThreadPool();
        
        try (ServerSocket ss = new ServerSocket(listeningPort)) {
            ss.setSoTimeout(2000);
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Socket client = ss.accept();
                    pool.execute(new ClientRequest(users, client, this.SocialNetworkIP, this.SocialNetworkPORT));
                } catch (SocketTimeoutException e) {
                    continue;
                }
            }        
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        

    }

    
}