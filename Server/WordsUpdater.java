package Server;

public class WordsUpdater implements Runnable {

    private UsersDatabase us;
    private long timer;

    public WordsUpdater(UsersDatabase us, long timer) {
        this.us = us;
        this.timer = timer;
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(timer);
                this.us.changeWord();
            } catch (InterruptedException e) {
                break;
            }
        }
    }
    
}
