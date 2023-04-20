package Interfaces;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.LinkedList;

/**
 * 
 * @interface                   ServerNotify
 * @brief                       Interfaccia per gestire le notifiche da parte del server
 * @author                      Simone Tassotti
 * @date                        20/04/2023
 * 
 */
public interface ServerNotify extends Remote {
    
    /**
     * 
     * @fun                                 ServerAlert
     * @brief                               Manda un alert al client
     * @param update                        Messaggi inviati dal server
     * @throws RemoteException
     * 
     */
    public void ServerAlert(LinkedList<String> update) throws RemoteException;

}
