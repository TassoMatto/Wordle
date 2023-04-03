package Server;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Random;

import Interfaces.ServerNotify;
import Server.Exception.StorageUserException;

/**
 * 
 * @class                           UserDatabase
 * @brief                           Gestione degli utenti del WordleServer
 * @author                          Simone Tassotti
 * @date                            23/03/2023
 * 
 */
public class UsersDatabase {
    
    /** Attributi */
    private BackupManager backup;
    private HashMap<String, Utente> database;
    private LinkedList<Utente> classifica;
    private HashMap<String, Utente> online;
    private LinkedList<String> words;
    private String secretWord;
    private long gameTime;
    private Thread wordsUpdate;

    /**
     * 
     * @fun                             UserDatabase
     * @brief                           Metodo costruttore di base
     * @throws FileNotFoundException
     * 
     */
    public UsersDatabase() throws FileNotFoundException {

        /** Costruzione strutture */
        this.database = new HashMap<>();
        this.classifica = new LinkedList<>();
        this.backup = new BackupManager();
        this.online = new HashMap<>();
        this.secretWord = "";
        if((this.words = uploadWords("words.txt")) == null) throw new FileNotFoundException();

    }

    /**
     * 
     * @fun                             UserDatabase
     * @brief                           Metodo costruttore
     * @param backupSaving              File dal quale recupero il vecchio stato del server
     * @param pathname                  File dizionario delle parole segrete da usare
     * @throws FileNotFoundException
     * 
     */
    public UsersDatabase(String backupSaving, String pathname) throws FileNotFoundException {

        /** Costruzione strutture */
        this.database = new HashMap<>();
        this.classifica = new LinkedList<>();
        this.backup = new BackupManager(backupSaving);
        this.online = new HashMap<>();
        this.secretWord = "";
        if((this.words = uploadWords(pathname)) == null) throw new FileNotFoundException();
        System.out.println(words.size());
    
        /** Ripristino informazioni */
        LinkedList<Utente> usersRecovery = (LinkedList<Utente>) this.backup.infoRecovery();
        if(usersRecovery != null) {
            for (Utente utente : usersRecovery) {
                this.database.put(utente.getUsername(), utente);
                this.classifica.add(utente);
            }
        }
        this.gameTime = 15000;
        this.wordsUpdate = new Thread(new WordsUpdater(this, gameTime));
        this.wordsUpdate.start();

    }

    public void changeWord() {
        synchronized(database) {
            classifica.sort(null);
            Iterator<Utente> i = online.values().iterator();
            while (i.hasNext()) {
                Utente u = i.next();
                try {                                
                    u.giveServerNotify().EndGame(secretWord);
                    u.resetGamePlayed();
                } catch (RemoteException e) {
                    e.printStackTrace();
                    break;
                }
            }
            Random r = new Random();
            secretWord = words.get(r.nextInt(0,words.size()));
            System.out.println("La parola segreta oggi: " + secretWord);
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
        LinkedList<String> w = new LinkedList<>();
        File f = new File(pathname);
        if(!f.exists() || !f.isFile()) return null;
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

    public boolean registerUser(String username, String password) {

        /** Controllo argomenti */
        if(username.equals("") || password.equals("")) throw new IllegalArgumentException();
        synchronized(database) {
            if(this.database.get(username) != null) {
                System.err.println("aaaaa");
                return false;
            }
            /** Creo utente da registrare */
            Utente u = new Utente(username, password);
            this.database.put(username, u);
            try {
                synchronized(this.backup) {
                    backup.updateUsers(this.database.values());
                }
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
            this.classifica.add(u);
        }

        return true;
    }

    public long loginUser(String username, String password) {

        /** Controllo argomenti */
        if(username.equals("") || password.equals("")) throw new IllegalArgumentException();

        /** Controllo l'esistenza dell'utente */
        Utente u;
        synchronized(this.database) {
            if((u = this.database.get(username)) == null) return -1;
        
            if(!u.checkUserPsw(password)) return -1;
            System.out.println("Log utente");

            System.out.println("Log utente");

            if(this.online.containsKey(username)) return -1;
            this.online.put(username, u);

            return System.currentTimeMillis();
        
        }
    }

    public void logoutUser(String username, String password) {

        /** Controllo argomenti */
        if(username.equals("") || password.equals("")) throw new IllegalArgumentException();

        /** Disconnetto l'utente */
        synchronized(database) {
            this.online.remove(username, password);
        }
    }

    public ArrayList<String> giveUserAttempt(String username, String password) {
        
        /** Controllo argomenti */
        if(username.equals("") || password.equals("")) throw new IllegalArgumentException();
        synchronized(database) {
            if((online.get(username) == null) || (!online.get(username).checkUserPsw(password)) || (!online.get(username).isPlaying())) return null;

            /** Recupero i suggerimenti dell'ultimo gioco dell'utente */
            Utente u;
                if((u = this.online.get(username)) == null) return null;
                if(!u.checkUserPsw(password)) return null;
                if(!u.isPlaying()) return null;
                return u.lastGameAttempts();
        }

        

    }

    public boolean playGame(String username, String password) {

        /** Controllo argomenti */
        if(username.equals("") || password.equals("")) throw new IllegalArgumentException();

        /** L'utente partecipa al gioco */
        synchronized(database) {
            Utente u;
            if((u = this.online.get(username)) == null) return false;
            if(u.isPlaying()) return true;
            u.addNewGamePlayed();
            synchronized(this.backup) {
                this.backup.updateUsers(this.online.values());
            }
        }
        
        return true;
    }

    public String sendGuessedWord(String username, String password, String gw) {
        
        /** Controllo argomenti */
        if(username.equals("") || password.equals("")) throw new IllegalArgumentException();
        StringBuilder s = new StringBuilder();
        synchronized(database) {
            if((online.get(username) == null) || (!online.get(username).checkUserPsw(password)) || (!online.get(username).isPlaying())) return null;
            if(online.get(username).numAttempts() == 12) return "maxAtt";

            synchronized(secretWord) {
                if(!words.contains(gw)) return "notFound";
                if(gw.equals(secretWord)) {
                    s.append("++++++++++");
                    online.get(username).addAttempt(s.toString());
                    try {
                        online.get(username).gameWin();
                    } catch (StorageUserException e) {
                        e.printStackTrace();
                        return null;
                    }
                    return s.toString();
                }
                for (int i = 0; i < gw.length(); i++) {
                    if(secretWord.charAt(i) == gw.charAt(i)) {
                        s.append("+");
                    } else if(secretWord.indexOf(gw.charAt(i)) == -1) {
                        s.append("X");
                    } else {
                        s.append("?");
                    }
                }
            }
            online.get(username).addAttempt(s.toString());

            synchronized(this.backup) {
                this.backup.updateUsers(this.online.values());
            }
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
            if((this.online.get(username) == null) || (!this.online.get(username).checkUserPsw(password))) throw new IllegalAccessError();
            this.online.get(username).unsetServerNotify();
        }
    }

}

