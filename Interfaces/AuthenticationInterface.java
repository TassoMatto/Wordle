package Interfaces;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface AuthenticationInterface extends Remote {
    
    public boolean register(String username, String password) throws RemoteException;

    public long login(String username, String password) throws RemoteException;

    public void registerClient(ServerNotify sn, String username, String password) throws RemoteException, IllegalAccessException;

    public void unregisterClient(String username, String password) throws RemoteException, IllegalAccessException;


}
