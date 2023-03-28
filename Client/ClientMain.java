package Client;

import Server.WordleServer;

/**
 * ClientMain
 */
public class ClientMain {

    public static void main(String[] args) {
        Thread t = new Thread(new WordleClient("config.txt"));
        t.start();
        t.join();
    }
}