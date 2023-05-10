package Server.Exception;

/**
 * 
 * @class           ErrorUserException
 * @brief           Errore nella memorizzazione delle informazioni dell'utente
 * @author          Simone Tassotti
 * @date            07/08/2023
 * @version         1.0
 * 
 */

public class StorageUserException extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * 
     * @fun             StoragerUserException
     * @brief           Costruttore di base
     * 
     */
    public StorageUserException() { super(); }

    /**
     * 
     * @fun             StoragerUserException
     * @brief           Metodo costruttore
     * @param error     Messaggio di errore
     * 
     */
    public StorageUserException(String error) { super(error); }

}