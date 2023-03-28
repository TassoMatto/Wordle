package Interfaces;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ServerNotify extends Remote {
    
    public void ServerAlert() throws RemoteException;

    public void EndGame() throws RemoteException;

}
