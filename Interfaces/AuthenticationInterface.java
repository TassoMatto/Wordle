package Interfaces;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * 
 * @fun                             AuthenticationInterface
 * @brief                           Interfaccia per la gestione dell'autenticazione di un client sul server
 * @author                          Simone Tassotti
 * @date                            20/04/2023
 * 
 */
public interface AuthenticationInterface extends Remote {
    
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
    public int register(String username, String password) throws RemoteException;

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
    public void registerClient(ServerNotify sn, String username, String password) throws RemoteException, IllegalAccessException;

}
