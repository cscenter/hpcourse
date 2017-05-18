package util;

public class SynchronizedInteger {
    private volatile int value;

    public SynchronizedInteger() {
        this.value = 0;
    }

    public synchronized int inc() throws InterruptedException{
        int result = value++;
        return result;
    }
}
