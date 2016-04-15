package main;

import java.util.Iterator;
import java.util.LinkedList;

public class SynchronizedQueue<E> {
    private final LinkedList<E> queue = new LinkedList<>();
    private static final Object monitor = new Object();

    public void put(E item) {
        synchronized (monitor) {
            queue.add(item);
            if (queue.size() == 1) {
                monitor.notify();
            }
        }
    }

    public E pop() throws InterruptedException {
        synchronized (monitor) {
            while (queue.isEmpty()) {
                monitor.wait();
            }

            return queue.pop();
        }
    }

    public Iterator<E> iterator() {
        return queue.listIterator();
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }
}
