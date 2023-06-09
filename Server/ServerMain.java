/**
 * 
 * @class                                       ServerMain
 * @brief                                       Batteria di test di esecuzione del WordleServer
 * @author                                      Simone Tassotti
 * @date                                        28/03/2023
 * 
 */
package Server;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;

public class ServerMain {
    public static void main(String[] args) {
        
        /** Controllo argomenti */
        if(args.length != 3) throw new IllegalArgumentException("Numero di argomenti");
        
        /** Prendo come unico argomento il file di config */
        String configFile = args[0];
        String backupFile = args[1];
        String words = args[2];

        /** Avvio server */
        try (BufferedReader br = new BufferedReader(new InputStreamReader(System.in))){
            Thread serverT = new Thread(new WordleServer(configFile, backupFile, words));
            serverT.start();

            /** Premere un qualsiasi tasto per avviare la fase di arresto del server */
            br.readLine();
            serverT.interrupt();
        } catch (FileNotFoundException fnfe) {
            System.err.println("<< FILE DI CONFIG NON TROVATO O IMPOSSIBILE APRIRLO >>\n"); 
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

    }
}
