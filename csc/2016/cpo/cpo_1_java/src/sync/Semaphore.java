package sync;

/**
 * Created by Pavel Chursin on 15.04.2016.
 */
public class Semaphore {
    private int count;
    private final int max;

    public Semaphore(int size) {
        count = size;
        max = size;
    }

    public synchronized void lock() {
        if (count > 0) {
            count--;
        } else
            try {
                while (count == 0)
                    wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
    }

    public synchronized void unlock() {
        if (count < max) {
            count++;
        }
        notify();
    }
}
