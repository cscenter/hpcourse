package ru.csc2015.concurrency;

import java.util.ArrayDeque;
import java.util.Queue;

public class ConcurrentQueue<T> {
    private final int MAX_CAPACITY = 1000;
    private final Queue<T> queue;

    public ConcurrentQueue() {
        queue = new ArrayDeque<>(MAX_CAPACITY);
    }

    public void push(T value) throws InterruptedException {
        if (value == null) {
            throw new IllegalArgumentException("task must be != null");
        }

        synchronized (queue) {
            while (queue.size() == MAX_CAPACITY) {
                queue.wait();
            }

            queue.add(value);
            queue.notify();
        }
    }

    public T pop() throws InterruptedException {
        synchronized (queue) {
            while (queue.size() == 0) {
                queue.wait();
            }

            final T value = queue.poll();
            queue.notify();
            return value;
        }
    }
}
