/**
 * 
 * @class                                       ServerMain
 * @brief                                       Batteria di test di esecuzione del WordleServer
 * @author                                      Simone Tassotti
 * @date                                        28/03/2023
 * 
 */

package Server;

import java.io.FileNotFoundException;

public class ServerMain {
    public static void main(String[] args) {
        
        /** Controllo argomenti */
        if(args.length != 2) throw new IllegalArgumentException("Numero di argomenti");
        
        /** Prendo come unico argomento il file di config */
        String configFile = args[0];
        String backupFile = args[1];

        /** Avvio server */
        try {
            Thread serverT = new Thread(new WordleServer(configFile, backupFile));
            serverT.start();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return;
        }

    }
}
