import java.util.concurrent.ForkJoinPool;

/**
 * Created by philipp on 01.05.15.
 */
public class Task implements Runnable {
    private static int sleepTime = 200;

    private int miliSeconds;
    private String name;

    public Task(int seconds, String name) {
        miliSeconds = seconds * 1000;
        this.name = name;
    }

    @Override
    public void run() {
//        System.out.println(name);
        while (miliSeconds > 0) {
            miliSeconds -= sleepTime;

            if (Thread.currentThread().interrupted()) {
                return;
            }

            try {
                Thread.sleep(sleepTime);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
