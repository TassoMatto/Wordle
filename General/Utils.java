package General;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Utils
 */
public class Utils {

    /**
     * 
     * @fun                     findParam
     * @brief                   Trova per il campo interessato il valore corrispondente in formato String
     * @param array             Matrice che contiene le coppie (parametro, valore)
     * @param param             Parametro che voglio filtrare
     * @return                  Ritorna il valore del parametro filtrato, "" se non esiste match con il parametro
     * 
     */
    public static String filterParam(String[][] array, String param) {
        for (int i = 0; i < array.length; i++) {
            if(array[i][0].equals(param)) return array[i][1];
        }
        return "";
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
    public static void sendMessage(DataOutputStream dos, String msg) throws IOException {

        /** Controllo argomenti */
        if((dos == null) || (msg == null)) throw new NullPointerException();

        /** Invio la dimensione e il messaggio */
        int reqDim = msg.length();
    
        dos.writeInt(reqDim);
        dos.write(msg.getBytes(), 0, reqDim);
    }

    /**
     * 
     * @fun                             getMsg
     * @brief                           Ricevo messaggio TCP dal server
     * @param dis                       InputStream
     * @return                          Il messaggio ricevuto, null in caso di errore
     * @throws IOException     
     * @throws NullPointerException
     * 
     */
    public static String receiveMessage(DataInputStream dis) throws IOException {

        /** Controllo argomento */
        if(dis == null) throw new NullPointerException();

        /** Resto in attesa di ricevere un messaggio dal server */
        int reqDim;
        byte[] reqByte;
        reqDim = dis.readInt();  
        reqByte = dis.readNBytes(reqDim);
        
        return new String(reqByte, 0, reqDim);
    }
    
}