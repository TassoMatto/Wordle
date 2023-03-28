package Server;

import java.rmi.RemoteException;

import Interfaces.AuthenticationInterface;
import Interfaces.ServerNotify;

public class Authentication implements AuthenticationInterface {

    /** Attributi */
    private UsersDatabase userDB;

    public Authentication(UsersDatabase userDB) {
        this.userDB = userDB;
    }

    @Override
    public boolean register(String username, String password) throws RemoteException {

        /** Controllo argomenti */
        if(username.equals("") || password.equals("")) throw new IllegalArgumentException();
        System.out.println("Funziona");

        return this.userDB.registerUser(username, password);
    }

    @Override
    public long login(String username, String password) throws RemoteException {
        
        /** Controllo argomenti */
        if(username.equals(password) || password.equals("")) throw new IllegalArgumentException();

        return this.userDB.loginUser(username, password);
    }

    @Override
    public void registerClient(ServerNotify sn) throws RemoteException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'registerClient'");
    }

    @Override
    public void unregisterClient(ServerNotify sn) throws RemoteException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'unregisterClient'");
    }
    
}
