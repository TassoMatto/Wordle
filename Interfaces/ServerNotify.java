package Interfaces;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.LinkedList;

public interface ServerNotify extends Remote {
    
    public void ServerAlert(LinkedList<String> update) throws RemoteException;

    public void EndGame(String oldWord) throws RemoteException;

}
