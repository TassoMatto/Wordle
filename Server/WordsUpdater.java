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
            System.out.println("Cambio parola");
            try {
                this.us.changeWord();
                System.out.println("Cambiata");
                System.out.flush();
                Thread.sleep(timer*1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
                break;
            }
        }
    }
    
}
