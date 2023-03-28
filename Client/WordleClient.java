package Client;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.Buffer;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import Interfaces.AuthenticationInterface;
import Server.Bootload;

public class WordleClient implements Runnable {

    private int RMI_PORT;
    private String socialIp;
    private int socialPORT;

    private String findParam(String[][] array, String param) {
        for (int i = 0; i < array.length; i++) {
            if(array[i][0].equals(param)) return array[i][1];
        }
        return "";
    }

    public WordleClient(String configFile) {
        try {
            String[][] param = Bootload.readFileConfig(configFile, "portSocialNetwork", "ipSocialNetwork", "portRMI");
            this.socialIp = findParam(param, "ipSocialNetwork");
            this.socialPORT = Integer.parseInt(findParam(param, "portSocialNetwork"));
            this.RMI_PORT = Integer.parseInt(findParam(param, "portRMI"));

        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return;
        }
    }

    private boolean registerRequest(BufferedReader br) {

        /** Recupero il riferimenti RMI del server */
        AuthenticationInterface ai;
        try {
            Registry r = LocateRegistry.getRegistry(this.RMI_PORT);
            ai = (AuthenticationInterface) r.lookup("//localhost/WordleServer");
            System.out.print("Username: ");
            String username = br.readLine();
            String password = br.readLine();
            return ai.register(username, password);
        } catch (NotBoundException | IOException e) {
            e.printStackTrace();
            return false;
        }
    } 

    @Override
    public void run() {
        
        try (BufferedReader br = new BufferedReader(new InputStreamReader(System.in))) {
            System.out.println("\t\tBENVENUTO SU WORDLE!\n\n");
            System.out.println("1) Registrazione nuovo utente sul server");
            System.out.println("2) Accesso utente");
            Integer opt = Integer.parseInt(br.readLine());
            switch (opt) {
                case 1:
                    if(registerRequest(br)) {
                        System.out.println("REGISTRAZIONE AVVENUTA CON SUCCESSO");
                    } else {
                        System.out.println("IMPOSSIBILE REGISTRARE L'UTENTE");
                    }
                break;
            }
        } catch (Exception e) {
            // TODO: handle exception
        }

    }



}
