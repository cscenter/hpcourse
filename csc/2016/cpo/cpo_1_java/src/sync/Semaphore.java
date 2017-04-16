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
        while (count == 0)
            try {
                wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        count--;
    }

    public synchronized void unlock() {
        if (count < max) {
            count++;
        }
        notify();
    }
}
