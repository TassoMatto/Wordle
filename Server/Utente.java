package Server;
import java.io.Serializable;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.LinkedList;

import com.fasterxml.jackson.annotation.JsonIgnore;

import Interfaces.ServerNotify;
import Server.Exception.StorageUserException;

/**
 * 
 * @class               Utente
 * @brief               Classe di gestione delle statistiche, raking di gioco di un utente della piattaforma
 * @author              Simone Tassotti
 * @date                22/04/2023
 * 
 */
public class Utente implements Serializable, Comparable<Utente> {
    
    /** Variabili globali */
    private static final int MAX_ATTEMPTS = 12;

    /** Attributi Utente */
    @JsonIgnore
    private ServerNotify clientSN;                          // Comunicazione tramite RMI al client
    private String username;                                // Username dell'utente
    private String password;                                // Password utente
    private int gamePlayed;                                 // Numero di partite giocate
    private LinkedList<Boolean> gamesWon;                   // Lista che indica i game vinti dall'utente
    private int nGamesWin;                                  // Numero di game vinti
    private int successGameRow;                             // Ultima fila di risultati utili consecutivi
    private int bestSuccessGameRow;                         // Migliore file di risultati utili consecutivi
    private LinkedList<ArrayList<String>> attemptString;    // Lista di tutti i tentativi per ogni partita giocata
    private int guessDistribution[];                        // Distribuzione dei tentativi impiegati per vincere i vari game
    @JsonIgnore
    private boolean playConcurrentGame;                     // Flag che indica se l'utente stava partecipando al gioco corrente o meno
    @JsonIgnore
    private String oldWord;                                 // Ultima parola secreta che l'utente stava cercando di indovinare
    private static final long serialVersionUID = 1L;        // Versione serializzazione in formato json di un utente

    /**
     * 
     * @fun             Utente
     * @brief           Metodo costruttore usato per ricostruire oggetto da file Json
     * 
     */
    public Utente() {

        /** Imposto i valori degli attributi JsonIgnore */
        this.playConcurrentGame = false;
        this.oldWord = "";
        this.clientSN = null;
    }

    /**
     * 
     * @fun                         Utente
     * @brief                       Metodo costruttore
     * @param username              Username dell'utente
     * @param password              Password dell'utente
     * @throws                      IllegalArgumentException
     * 
     */
    public Utente(String username, String password) {

        /** Controllo argomenti */
        if((password.equals("")) || (username.equals(""))) throw new IllegalArgumentException();

        /** Imposto valori iniziali utenti */
        this.username = username;
        this.password = password;
        this.gamePlayed = 0;
        this.gamesWon = new LinkedList<>();
        this.successGameRow = 0;
        this.nGamesWin = 0;
        this.bestSuccessGameRow = 0;
        this.attemptString = new LinkedList<>();
        this.playConcurrentGame = false;
        this.guessDistribution = new int[12];
        this.oldWord = "";
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            this.guessDistribution[i] = 0;
        }
        this.clientSN = null;
    }

    /**
     * 
     * @fun                         getUsername
     * @brief                       Restuisce l'username dell'utente
     * @return                      Username dell'utente
     * 
     */
    public String getUsername() {
        return username;
    }

    /**
     *
     * @fun                 addAttempt
     * @brief               Aggiungo una stringa di suggerimenti che poi l'utente può condividere 
     * @param attempt       Tentativo per una parola del dizionario, corrispondenti ad un tentativo immesso nel sistema
     * @throws              IllegalArgumentException
     * @return              true, ho aggiunto un suggerimento, false altrimenti
     * 
     */
    public boolean addAttempt(String attempt) {

        /** Controllo argomenti */
        if(attempt.equals("")) throw new IllegalArgumentException();
        if((this.attemptString.size() == 0) || (this.attemptString.getLast().size() == MAX_ATTEMPTS)) return false;
        
        this.attemptString.getLast().add(attempt);

        return true;

    }

    /**
     * 
     * @fun                     addNewGamePlayed
     * @brief                   L'utente ha partecipato ad un nuovo gioco
     * 
     */
    public void addNewGamePlayed() {
        this.playConcurrentGame = true;
        this.gamePlayed++;
        this.attemptString.add(new ArrayList<>());
        if((this.gamesWon.size() == 0) || (!this.gamesWon.getLast())) this.successGameRow = 0;
        this.gamesWon.add(false);
    }

    /**
     * 
     * @fun                     gameWin
     * @brief                   Dichiara vinto l'ultimo gioco a cui a partecipato l'utente
     * @throws                  StorageUserException
     * 
     */
    public void gameWin() throws StorageUserException {

        /** Errore nella struttura dell'utente */
        if(this.attemptString.getLast().size() == 0) throw new StorageUserException();
        this.gamesWon.set(this.gamePlayed-1, true);
        this.nGamesWin++;
        this.successGameRow++;
        int count = 0;
        if(attemptString.getLast() != null) {
            for (String attempt : attemptString.getLast()) {
                count++;
            }
        }
        
        this.guessDistribution[count]++;

        this.bestSuccessGameRow = (this.successGameRow > this.bestSuccessGameRow) ? this.successGameRow : this.bestSuccessGameRow;
    }

    /**
     * 
     * @fun                 lastGameAttempts
     * @brief               Ritorna la lista di tentativi effettuati dall'utente per cercare di indovinare l'ultima parola
     * @return              In caso di successo ritorna la lista di tentativi effettuati dall'utente, null in caso di nessun
     *                      tentativo effettuato o in caso di errore
     * 
     */
    public ArrayList<String> lastGameAttempts() {
        if(!winLastGame()) return null;
        ArrayList<String> tmp = null;

        if(this.attemptString.size() != 0) tmp = new ArrayList<>(this.attemptString.getLast());

        return tmp;
    }

    /**
     * 
     * @fun                 awsUtente
     * @brief               Calcola il punto aws per il singolo utente
     * @return              Il punteggio aws dell'utente in questione
     * 
     */
    public double awsUtente() {
        double score = 0;
        for (int i = 0; i < guessDistribution.length; i++) {
            score += (i+1)*this.guessDistribution[i];
        }
        score += (MAX_ATTEMPTS+1)*(this.gamePlayed-this.nGamesWin);
        return (score / gamePlayed);
    }

    /**
     * 
     * @fun                             checkUserPsw
     * @brief                           Controlla se la password sottomessa coincida con quella dell'utente
     * @param password                  Password che si vuole controllare
     * @return                          True se le due password coincidono, false altrimenti
     * 
     */
    public boolean checkUserPsw(String password) {
        return password.equals(this.password);
    }

    /**
     * 
     * @fun                             resetGamePlayed
     * @brief                           Resetta il flag che indica che l'utente stava giocando al gioco
     * @throws RemoteException
     * 
     */
    public void resetGamePlayed(String oldWord) throws RemoteException {
        if(playConcurrentGame) {
            this.oldWord = oldWord;
            this.playConcurrentGame = false;
        }
    }

    /**
     * 
     * @fun                             isPlaying
     * @brief                           Mi indica se l'utente sta partecipando ad un gioco o no
     * @return                          true se l'utente sta partecipando a qualche game, false altrimenti
     * 
     */
    @JsonIgnore
    public boolean isPlaying() {
        return this.playConcurrentGame;
    }

    /**
     * 
     * @fun                 alertEndGame
     * @brief               Controlla se all'utente ha finito di giocare
     * @return              Ritorna la vecchia parola che era da indovinare, "" altrimenti
     * 
     */
    public String alertEndGame() {
        String ret = this.oldWord;
        this.oldWord = "";
        return ret;
    }

    /**
     * 
     * @fun                 winLastGame
     * @brief               Controlla se l'utente ha vinto l'ultimo gioco a cui ha partecipato
     * @return              true o false a seconda se ha vinto o meno
     * 
     */
    public boolean winLastGame() {
        return this.gamesWon.getLast().booleanValue();
    }

    /**
     * 
     * @fun                 setServerNotify
     * @brief               Imposta il servizio di notifiche ai client
     * @param sn            Riferimento RMI del client da notificare
     * 
     */
    public void setServerNotify(ServerNotify sn) {
        this.clientSN = sn;
    }

    /**
     * 
     * @fun         unsetServerNotify
     * @brief       Cancella riferimento RMI del client collegato come l'utente corrente
     * 
     */
    public void unsetServerNotify() {
        this.clientSN = null;
    }

    /**
     * 
     * @fun                 toString
     * @brief               Rende l'utente in formato String, ovvero mostra le statistiche
     * 
     */
    @Override
    public String toString() {
        StringBuilder s = new StringBuilder();

        s.append("Numero di partite giocate :" +  this.gamePlayed).append("\n");
        s.append("Percentuale partite vinte :" + (this.nGamesWin/this.gamePlayed)*100).append("\n");
        s.append("Ultima striscia di vittore consecutive: " + this.successGameRow).append("\n");
        s.append("Migliore striscia di vittorie consecutive: " + this.bestSuccessGameRow).append("\n");
        s.append("Guess distribution: ").append("\n");
        for (int i = 0; i < guessDistribution.length; i++) {
            s.append(i + " tentativi: " + this.guessDistribution[i]).append("\n");
        }

        return s.toString();
    }

    /**
     * 
     * @fun                     numAttempts
     * @brief                   Restituisce il numero di tentativi effettuati nell'ultimo gioco
     * @return                  Il numero di tentativi dell'ultimo gioco
     * 
     */
    public int numAttempts() {
        return this.attemptString.getLast().size();
    }

    /**
     * 
     * @fun                 compareTo
     * @brief               Confronto tra due utenti tramite aws
     * @param o             Utente da confrontare con quello corrente
     * @return              Ritorna la differenza tra this e l'oggetto passato
     * 
     */
    @Override
    public int compareTo(Utente o) {
        return Double.compare(this.awsUtente(), o.awsUtente()); 
    }

    /**
     * 
     * @fun                 giveServerNotify
     * @brief               Restituisce l'attributo sn
     * @return              Ritorna sn
     * 
     */
    public ServerNotify giveServerNotify() {
        return this.clientSN;
    }

}
