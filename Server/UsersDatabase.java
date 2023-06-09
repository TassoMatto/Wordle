package Server;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;
import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.Logger;

import javax.net.ssl.SSLSocketFactory;

import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;

import Interfaces.ServerNotify;
import Server.Exception.StorageUserException;

/**
 * 
 * @class                           UserDatabase
 * @brief                           Gestione degli utenti del WordleServer
 * @author                          Simone Tassotti
 * @date                            24/04/2023
 * 
 */
public class UsersDatabase {
    
    /** Attributi */
    @JsonIgnore
    private BackupManager backup;
    private HashMap<String, Utente> database;
    @JsonIgnore
    private LinkedList<Utente> classifica;
    @JsonIgnore
    private HashMap<String, Utente> online;
    @JsonIgnore
    private LinkedList<String> words;
    @JsonIgnore
    private String secretWord;
    private int totalWords;
    @JsonIgnore
    private long gameTime;
    @JsonIgnore
    private Thread wordsUpdate;
    @JsonIgnore
    private String translated;
    @JsonIgnore
    private Logger log;


    /********** METODI PRIVATI **********/
    
    /**
     * 
     * @fun                 updatePlaces
     * @brief               Si occupa di aggiornare la classifica di gioco
     * 
     */
    private void updatePlaces() {

        /** Aggiorno la classifica */
        this.log.warning(Thread.currentThread().getName() + " Aggiornamento classifica\n");
        this.classifica.sort(null);
        LinkedList<String> l = new LinkedList<>();
        Iterator<Utente> i = this.classifica.iterator();
        int count = 0;
        while (i.hasNext() && count < 3) {
            Utente u = i.next();
            l.add((count+1) + ") Utente " + u.getUsername() + " - Punteggio: " + u.awsUtente());
            count++;
        }
               
        /** Avverto gli utenti di un cambiamento in classifica */
        this.log.info(Thread.currentThread().getName() + " Notifico aggiornamento classifica agli utenti\n");
        i = online.values().iterator();
        while (i.hasNext()) {
            Utente u = i.next();
            try {
                u.giveServerNotify().ServerAlert(l);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 
     * @fun                 httpRequest
     * @brief               Effettua una richiesta http per tradurre la parola segreta
     * @return              Parola tradotta in caso di successo, null altrimenti
     * 
     */
    private String httpRequest() {

        /** Controllo argomenti */
        if(secretWord.equals("")) return null;

        /** Ricavo indizzo e porta del sito */
        URL url;
        try {
            url = new URL("https://api.mymemory.translated.net");
        } catch (MalformedURLException e) {
            e.printStackTrace();
            return null;
        }
        String hostname = url.getHost();
        int port = 443;
        SSLSocketFactory ssf = (SSLSocketFactory) SSLSocketFactory.getDefault();

        /** Inizio comunicazione */
        try (
            Socket socket = ssf.createSocket(hostname, port);
            PrintWriter output = new PrintWriter(socket.getOutputStream());
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        ) {

            /** Formalizzo richiesta HTTP e la invio */
            this.log.info(Thread.currentThread().getName() + " In attesa di ricevere la parola segreta tradotta\n");
            String m = "GET /get?q=" + secretWord + "&langpair=en|it HTTP/1.1\r\n"
            + "Host: " + hostname + "\r\n"
            + "Connection: close\r\n"
            + "\r\n";
            this.log.warning(Thread.currentThread().getName() + " Richiesta https inviata: " + m + "\n");
            output.write(m);
            output.flush();

            /** Attendo la risposta dal server */
            String line, save = "";
            while ((line = reader.readLine()) != null) {
                if(line.contains("{")) save = line;
            }

            /** Estraggo le informazioni dal JSON inviatomi */
            JsonFactory factory = new JsonFactory();
            ObjectMapper mapper = new ObjectMapper();
            JsonParser parser = factory.createParser(save);
            mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
            parser.setCodec(mapper);
            JsonNode rootNode = mapper.readTree(save);  

            Iterator<Map.Entry<String,JsonNode>> fieldsIterator = rootNode.fields();
            
            while (fieldsIterator.hasNext()) {

                Map.Entry<String,JsonNode> field = fieldsIterator.next();
                if(field.getKey().equals("responseData")) {
                    JsonNode translatedValue = field.getValue();
                    if(translatedValue.get("translatedText") != null) return translatedValue.get("translatedText").textValue();
                }
            }
            this.log.info(Thread.currentThread().getName() + " Parola tradotta ricevuta");
            return save;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 
     * @fun                         uploadWords
     * @brief                       Carica dal dizionario in memoria le parole da usare
     * @param pathname              File dal quale caricare il dizionario di parole
     * @return                      Il dizionario di parole, null altrimenti
     * 
     */
    private LinkedList<String> uploadWords(String pathname) {
        
        /** Controllo esistenza file */
        LinkedList<String> w = new LinkedList<>();
        File f = new File(pathname);
        if(!f.exists() || !f.isFile()) return null;
        /** Upload delle parole segrete */
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String s = br.readLine();
            while ((s = br.readLine()) != null) {
                w.add(s);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        
        return w;
    }

    /************************************/

    /**
     * 
     * @fun                     UserDatabase
     * @brief                   Metodo costruttore
     * 
     */
    public UsersDatabase() {}

    /**
     * 
     * @fun                             UserDatabase
     * @brief                           Metodo costruttore
     * @param backupSaving              File dal quale recupero il vecchio stato del server
     * @param pathname                  File dizionario delle parole segrete da usare
     * @param timegame                  Tempo di gioco per ogni parola
     * @param log                       File di log
     * @throws FileNotFoundException
     * 
     */
    public UsersDatabase(String backupSaving, String dictionary, long timegame, Logger log) throws FileNotFoundException {

        /** Controllo argomenti */
        if(dictionary == null) throw new NullPointerException();
        if(backupSaving == null) throw new NullPointerException();

        /** Costruzione strutture */
        this.database = new HashMap<>();
        this.classifica = new LinkedList<>();
        this.backup = new BackupManager(backupSaving);
        this.online = new HashMap<>();
        this.secretWord = "";
        if((this.words = uploadWords(dictionary)) == null) throw new FileNotFoundException();
    
        /** Ripristino informazioni */
        UsersDatabase ud = this.backup.infoRecovery();
        if(ud != null) {
            List<Utente> list = List.copyOf(ud.exportUsers());
            for (Utente utente : list) {
                this.database.put(utente.getUsername(), utente);
                this.classifica.add(utente);
            }
            this.totalWords = ud.giveTotalWord();
            System.out.println("LE PAROLE: " + this.totalWords);
        } else {
            this.totalWords = 0;
        }
       
        this.log = log;
        this.gameTime = timegame;
        this.translated = "";
        this.wordsUpdate = new Thread(new WordsUpdater(this, gameTime, log));
        this.wordsUpdate.start();

    }

    /** 
     * 
     * @fun             stopDatabase
     * @brief           Avvia fase di arresto del database
     * 
     */
    public void stopDatabase() {

        /** Invio interrupt ai vari thread gestiti */
        this.log.warning(Thread.currentThread().getName() + " Sto fermando il database\n");
        this.wordsUpdate.interrupt();
        try {
            this.wordsUpdate.join();
            this.backup.updateUsers(this);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        this.log.warning(Thread.currentThread().getName() + " Database fermato\n");

    }

    /**
     * 
     * @fun                 exportUsers
     * @brief               Esporta gli utenti del DB
     * @return              Collezione di utenti
     * 
     */
    public Collection<Utente> exportUsers() {
        return this.database.values();
    }

    /**
     * 
     * @fun             giveTotalWord
     * @brief           Restituisce il numero totale di parole pubblicate
     * @return          Totale di parole pubblicate
     * 
     */
    public int giveTotalWord() {
        return this.totalWords;
    }

    /**
     * 
     * @fun                     changeWord
     * @brief                   Cambia la parola segreta del server
     * @throws RemoteException
     * 
     */
    public void changeWord() {
        synchronized(database) {
            
            Iterator<Utente> i = online.values().iterator();
            this.log.warning(Thread.currentThread().getName() + " Pubblicazione nuova parola\n");
            while (i.hasNext()) {
                Utente u = i.next();
                try {       
                    if(u.isPlaying() && !u.winLastGame()) u.resetGamePlayed(translated);
                    else u.resetGamePlayed("");
                } catch (RemoteException e) {
                    e.printStackTrace();
                    break;
                }
            }
            Random r = new Random();
            secretWord = words.get(r.nextInt(0,words.size()));
            translated = httpRequest();
            this.totalWords++;
            this.log.warning(Thread.currentThread().getName() + "Parola aggiornata (Originale: " + secretWord + " - Tradotta: " + translated + ")\n");
            updatePlaces();
        }
    }

    /**
     * 
     * @fun                             registerUser
     * @brief                           Registro un utente sul database
     * @param username                  Nome utente del giocatore
     * @param password                  Passoword utente
     * @throws IllegalArgumentException
     * @return                          (0) in caso di successo
     *                                  (1) Utente già registrato
     *                                  (-1) Errore
     * 
     */
    public int registerUser(String username, String password) {

        /** Controllo argomenti */
        if(username.equals("") || password.equals("")) throw new IllegalArgumentException();
        synchronized(database) {
            if(this.database.get(username) != null) {
                return 1;
            }

            /** Creo utente da registrare */
            Utente u = new Utente(username, password);
            this.database.put(username, u);
            try {
                backup.updateUsers(this);
            } catch (Exception e) {
                e.printStackTrace();
                return -1;
            }
            this.classifica.add(u);
        }

        return 0;
    }

    /**
     * 
     * @fun                                 loginUser
     * @brief                               Accesso di un utente nel server di gioco
     * @param username                      Username del giocatore
     * @param password                      Password utente
     * @throws IllegalArgumentException 
     * @return                              (0) In caso di successo
     *                                      (1) Utente non registrato
     *                                      (2) Password errata
     *                                      (3) Utente già loggato
     *                                      (-1) Errore
     * 
     */
    public int loginUser(String username, String password) {

        /** Controllo argomenti */
        if(username.equals("") || password.equals("")) throw new IllegalArgumentException();

        /** Controllo l'esistenza dell'utente */
        Utente u;
        synchronized(this.database) {
            if((u = this.database.get(username)) == null) return 1;
        
            if(!u.checkUserPsw(password)) return 2;
            if(this.online.containsKey(username)) return 3;
            if(this.online.put(username, u) != null) return -1;

            return 0;
        }
    }

    /**
     * 
     * @fun                                 logoutUser
     * @brief                               Disconnessione dell'utente dal server
     * @param username                      Username utente
     * @param password                      Password utente
     * @throws IllegalArgumentException
     * 
     */
    public void logoutUser(String username, String password) {

        /** Controllo argomenti */
        if(username.equals("") || password.equals("")) throw new IllegalArgumentException();

        /** Disconnetto l'utente */
        synchronized(database) {
            Utente u = this.online.get(username);
            if(u == null) return;
            if(!u.checkUserPsw(password)) return;
            u.logout();
            this.online.remove(username);
        }
    }

    /**
     * 
     * @fun                                 giveUserAttempt
     * @brief                               Restituisce i suggerimenti di gioco dell'ultima parola che l'utente ha provato a indovinare
     * @param username                      Username utente
     * @param password                      Password utente
     * @throws IllegalArgumentException
     * @return                              Lista di suggerimenti dell'ultimo gioco a cui a partecipato, null altrimenti
     * 
     */
    public ArrayList<String> giveUserAttempt(String username, String password) {
        
        /** Controllo argomenti */
        if(username.equals("") || password.equals("")) throw new IllegalArgumentException();
        synchronized(database) {
            if((online.get(username) == null) || (!online.get(username).checkUserPsw(password))) return null;

            /** Recupero i suggerimenti dell'ultimo gioco dell'utente */
            Utente u;
            if((u = this.online.get(username)) == null) return null;
            if(!u.checkUserPsw(password)) return null;
            return u.lastGameAttempts();
        }

        

    }

    /**
     * 
     * @fun                                 playGame
     * @brief                               Richiesta di partecipare all'ultimo gioco
     * @param username                      Username utente
     * @param password                      Password utente
     * @throws IllegalArgumentException     
     * @return                              (0) In caso di successo
     *                                      (1) Utente non è online
     *                                      (2) Utente ha vinto il gioco corrente
     *                                      (3) Se l'utente ha finito i tentativi
     *                                      ()
     * 
     */
    public int playGame(String username, String password) {

        /** Controllo argomenti */
        if(username.equals("") || password.equals("")) throw new IllegalArgumentException();

        /** L'utente partecipa al gioco */
        synchronized(database) {
            Utente u;
            if((u = this.online.get(username)) == null) return 1;
            if(u.isPlaying() && u.winLastGame()) return 2;
            if(u.isPlaying()) return 3;
            
            int res;
            try {
                res = u.addNewGamePlayed(secretWord);
            } catch (Exception e) {
                e.printStackTrace();
                return 1;
            }
            this.backup.updateUsers(this);
            updatePlaces();

            return res;
        }
        
        
    }

    public String sendGuessedWord(String username, String password, String gw) {
        /** Controllo argomenti */
        if(username.equals("") || password.equals("")) throw new IllegalArgumentException();
        StringBuilder s = new StringBuilder();
        synchronized(database) {
            
            if((online.get(username) == null) || (!online.get(username).checkUserPsw(password))) return null;
            String wt = online.get(username).alertEndGame();
            if(!wt.equals("")) return "timeout_" + wt;
            if(!online.get(username).isPlaying()) return "notAllow";
            if(online.get(username).winLastGame()) return "justWin";
            if(online.get(username).numAttempts() == 12) return "maxAtt_" + translated;

            String save = secretWord;
            if(!words.contains(gw)) return "notFound";
            if(gw.equals(secretWord)) {
                s.append("++++++++++");
                try {
                    online.get(username).gameWin();
                } catch (StorageUserException e) {
                    e.printStackTrace();
                    return null;
                }
                online.get(username).addAttempt(s.toString());
                this.backup.updateUsers(this);
                updatePlaces();
                return "win_" + translated;
                
            } else {
                for (int i = 0; i < gw.length(); i++) {
                    System.out.println(i + "------" + gw + "-----" + secretWord);
                    System.out.println("--->>>>>> " + save);
                    if(save.charAt(i) == gw.charAt(i)) {
                        char[] edit = save.toCharArray();
                        edit[i] = '-';
                        save = new String(edit);
                        s.append("+");
                    } else if(save.indexOf(gw.charAt(i)) == -1) {
                        s.append("X");
                    } else {
                        char[] edit = save.toCharArray();
                        int ind = save.indexOf(gw.charAt(i));
                        edit[ind] = '-';
                        save = new String(edit);
                        s.append("?");
                    }
                }
                
            }
            
            online.get(username).addAttempt(s.toString());
            this.backup.updateUsers(this);
            updatePlaces();
        }
        
        return s.toString();
    }

    public String userStatistics(String username, String password) {

        /** Controllo argomenti */
        if(username.equals("") || password.equals("")) throw new IllegalArgumentException();
        synchronized(database) {
            if((online.get(username) == null) || (!online.get(username).checkUserPsw(password))) return null;
            return this.online.get(username).toString();
        }


    }

    public void alertClientService(String username, String password, ServerNotify sn) throws IllegalAccessException {

        /** Controllo argomenti */
        if(username.equals("") || password.equals("")) throw new IllegalArgumentException();

        synchronized(database) {
            if((this.online.get(username) == null) || (!this.online.get(username).checkUserPsw(password))) throw new IllegalAccessError();
            this.online.get(username).setServerNotify(sn);
        }
    }

    public void disableClientAlert(String username, String password) throws IllegalAccessException, NoSuchObjectException {

        /** Controllo argomenti */
        if(username.equals("") || password.equals("")) throw new IllegalAccessException();

        synchronized(database) {
            if((this.database.get(username) == null) || (!this.database.get(username).checkUserPsw(password))) throw new IllegalAccessError();
            this.database.get(username).unsetServerNotify();
        }
    }

}


