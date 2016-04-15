package util;


/**
 * Created by scorpion on 14.04.16.
 */
public class SynchronizedInt {
    private volatile int currentValue;

    public SynchronizedInt() {
        currentValue = 0;
    }

    public synchronized int nextValue() {
        return ++currentValue;
    }
}
