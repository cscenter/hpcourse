import java.util.List;
import java.util.concurrent.ForkJoinPool;

/**
 * Created by philipp on 01.05.15.
 */
public class Task implements Runnable {
    protected static int sleepTime = 200;

    public int miliSeconds;
    public String name;

    public Task(int miliSeconds, String name) {
        this.miliSeconds = miliSeconds;
        this.name = name;
    }

    @Override
    public void run() {
//        System.out.println(name);
//        List a = null; // для теста на исключение
//        a.get(5);
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
