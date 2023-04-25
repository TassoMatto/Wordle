package General;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.Properties;

/**
 * 
 * @class           Bootload
 * @brief           Avvio di un server leggendo le configurazioni da file di testo
 * @author          Simone Tassotti
 * @date            08/03/2023
 * 
 */

public class ReadConfigFile {
    
    /**
     * 
     * @fun                                 readFileConfig
     * @brief                               Leggo un file di config ed estrapolo i parametri (coppia parametro=valore)
     * @param fileConfig                    Nome del file di config da leggere
     * @param optToRead                     Le opzioni che voglio debbano essere considerate
     * @return                              Restituisce la lista di opzioni con i rispettivi campi (come valore di un campo se uguale a "" indica campo non trovato),
     *                                      altrimenti null in caso di errore
     * @throws FileNotFoundException
     */
    public static String[][] readFileConfig(String fileConfig, String ...optToRead) throws FileNotFoundException {

        /** Controllo se esiste file di config */
        String serverConfig[][] = null;
        File configFile = new File(fileConfig);
        if(!configFile.exists() || !configFile.isFile()) throw new FileNotFoundException();
        Properties prop = new Properties();
        try (FileInputStream fin = new FileInputStream(configFile)) {
            prop.load(fin);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        if(prop.size() != 0) {
            serverConfig = new String[optToRead.length][2];
        }
        int c = 0;
        for (String string : optToRead) {
            serverConfig[c][0] = string;
            serverConfig[c][1] = (prop.containsKey(string)) ? prop.getProperty(string) : "";
            c++;
        }

        return serverConfig;
    }
}
