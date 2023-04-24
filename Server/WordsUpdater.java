package Server;

import java.util.logging.Logger;

/**
 * 
 * @class                       WordsUpdater
 * @brief                       Classe che gestisce un thread che si occupa di aggiornare le parole del server di gioco
 * @author                      Simone Tassotti
 * @date                        21/04/2023
 * 
 */
public class WordsUpdater implements Runnable {

    /** Attributi oggetto */
    private UsersDatabase us;
    private long timer;
    private Logger log;

    /**
     * 
     * @fun                     WordsUpdater
     * @brief                   Metodo costruttore
     * @param us                Database di gioco
     * @param timer             Tempo di gioco prima di pubblicare una nuova parola
     * @param log               File di log
     * 
     */
    public WordsUpdater(UsersDatabase us, long timer, Logger log) {
        this.us = us;
        this.timer = timer;
        this.log = log;
    }

    /**
     * 
     * @fun                 run
     * @brief               Ciclicamente aggiorna le parole del server
     * @throws              InterruptedException
     * 
     */
    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                this.us.changeWord();
                this.log.info(Thread.currentThread().getName() + " Cambio parola da indovinare\n");
                Thread.sleep(timer*1000);
            } catch (InterruptedException e) {
                break;
            }
        }
    }
    
}
