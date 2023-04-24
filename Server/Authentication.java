package Server;

import java.rmi.RemoteException;

import Interfaces.AuthenticationInterface;
import Interfaces.ServerNotify;

/**
 * 
 * @class                           Authentication
 * @brief                           Classe che gestisce tramite RMI le richieste di registrazione e segnalazione al server di gioco
 * @author                          Simone Tassotti
 * @date                            12/04/2023
 * 
 */

public class Authentication implements AuthenticationInterface {

    /** Attributi */
    private UsersDatabase userDB;

    /**
     * 
     * @fun                     Authentication
     * @brief                   Metodo costruttore
     * @param userDB            Database a cui fa riferimento
     * 
     */
    public Authentication(UsersDatabase userDB) {
        this.userDB = userDB;
    }

    /**
     * 
     * @fun                                 register
     * @brief                               Registrazione di un client sul server di gioco
     * @param username                      Username dell'utente
     * @param password                      Password dell'utente
     * @return                              (1) Utente già registrato
     *                                      (0) Utente registrato correttamente
     *                                      (-1) Altrimenti
     * @throws RemoteException
     */
    @Override
    public int register(String username, String password) throws RemoteException {

        /** Controllo argomenti */
        if(username.equals("") || password.equals("")) throw new IllegalArgumentException();

        return this.userDB.registerUser(username, password);
    }

    /**
     * 
     * @fun                                 registerClient
     * @brief                               Registrazione del client al servizio di notifiche del server
     * @param sn                            Riferimento RMI del client
     * @param username                      Username dell'utente
     * @param password                      Password dell'utente
     * @throws RemoteException              
     * @throws IllegalAccessException
     */
    @Override
    public void registerClient(ServerNotify sn, String username, String password) throws RemoteException, IllegalAccessException {
        
        /** Controllo argomenti */
        if((sn == null) || (username.equals("")) || (password.equals(""))) throw new IllegalArgumentException();

        /** Registrazione al servizio di notifiche del server */
        this.userDB.alertClientService(username, password, sn);
    }

    
}
