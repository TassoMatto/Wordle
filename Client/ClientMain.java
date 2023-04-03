package Client;


/**
 * 
 * @class                       ClientMain
 * @brief                       Classe Main del Wordle Client
 * @author                      Simone Tassotti
 * @date                        28/03/2023
 * 
 */
public class ClientMain {

    public static void main(String[] args) {

        /** Avvio il thread WordleClient */
        Thread t = new Thread(new WordleClient("config.txt"));
        t.start();
        try {
            t.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }            
        System.exit(0);
    }
}