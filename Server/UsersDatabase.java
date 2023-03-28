package Server;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

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
    private Thread wordsUpd;
    private long gameTime;

    /**
     * 
     * @class                       WordsUpdater
     * @brief                       Sottoclasse che gestisce l'aggiornamento automatico della parola segreta
     * @author                      Simone Tassotti
     * @date                        23/03/2023
     * 
     */
    private class WordsUpdater implements Runnable {

        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                synchronized(online) {
                    classifica.sort(null);
                    Iterator<Utente> i = online.values().iterator();
                    while (i.hasNext()) {
                        Utente u = i.next();
                        u.resetGamePlayed();
                    }
                    synchronized(secretWord) {
                        Random r = new Random();
                        secretWord = words.get(r.nextInt(0,words.size()));
                    }
                }
                try {
                    Thread.sleep(gameTime*1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    return;
                }  
            }
        }
        
    }

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
        this.wordsUpd = new Thread(new WordsUpdater());
        this.wordsUpd.start();

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
        
        this.wordsUpd = new Thread(new WordsUpdater());
        this.wordsUpd.start();

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
        if(this.database.get(username) != null) {
            System.err.println("aaaaa");
            return false;
        }

        /** Creo utente da registrare */
        Utente u = new Utente(username, password);
        synchronized(this.database) {
            this.database.put(username, u);
            try {
                backup.updateDatabase(u);
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }
        
        synchronized(this.classifica) {
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
        }
        if(!u.checkUserPsw(password)) return -1;
        synchronized(this.online) {
            if(this.online.containsKey(username)) return -1;

            return System.currentTimeMillis();
        }
    }

    public void logoutUser(String username, String password) {

        /** Controllo argomenti */
        if(username.equals("") || password.equals("")) throw new IllegalArgumentException();

        /** Disconnetto l'utente */
        synchronized(this.online) {
            this.online.remove(username, password);
        }
    }

    public ArrayList<String> giveUserAttempt(String username, String password) {
        
        /** Controllo argomenti */
        if(username.equals("") || password.equals("")) throw new IllegalArgumentException();
        synchronized(this.online) {
            if((online.get(username) == null) || (!online.get(username).checkUserPsw(password)) || (!online.get(username).isPlaying())) return null;

            /** Recupero i suggerimenti dell'ultimo gioco dell'utente */
            Utente u;
            synchronized(this.online) {
                if((u = this.online.get(username)) == null) return null;
                if(!u.checkUserPsw(password)) return null;
                if(!u.isPlaying()) return null;
                return u.lastGameAttempts();
            }
        }

        

    }

    public boolean playGame(String username, String password) {

        /** Controllo argomenti */
        if(username.equals("") || password.equals("")) throw new IllegalArgumentException();

        /** L'utente partecipa al gioco */
        synchronized(this.online) {
            Utente u;
            if((u = this.online.get(password)) == null) return false;
            if(u.isPlaying()) return true;
            u.addNewGamePlayed();
        }

        return true;
    }

    public String sendGuessedWord(String username, String password, String gw) {
        
        /** Controllo argomenti */
        if(username.equals("") || password.equals("") || gw.equals("")) throw new IllegalArgumentException();
        StringBuilder s = new StringBuilder();
        synchronized(this.online) {
            if((online.get(username) == null) || (!online.get(username).checkUserPsw(password)) || (!online.get(username).isPlaying())) return null;
            
            synchronized(secretWord) {
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
            
        }
        
        return s.toString();
    }

    public String userStatistics(String username, String password) {

        /** Controllo argomenti */
        if(username.equals("") || password.equals("")) throw new IllegalArgumentException();
        synchronized (this.online) {
            if((online.get(username) == null) || (!online.get(username).checkUserPsw(password))) return null;
            return this.online.get(username).toString();
        }


    }

}


