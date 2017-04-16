package server.storage;

/**
 * @author Dmitriy Tseyler
 */
public class Counter {
    private int value;

    public Counter() {
        value = 0;
    }

    public synchronized int next() {
        return ++value;
    }

    public synchronized int current() {
        return value;
    }
}
