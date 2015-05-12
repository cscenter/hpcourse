package com.den.concurrency;

/**
 * @author Zaycev Denis
 */
public class ThreadPool {

    public static final int DEFAULT_THREADS_COUNT = 16;

    private final WorkingQueue workingQueue;

    public ThreadPool() {
        this(DEFAULT_THREADS_COUNT);
    }

    public ThreadPool(int threadsCount) {
        workingQueue = new WorkingQueue(threadsCount);
    }

    public void submit(Runnable task) {
        workingQueue.execute(task);
    }

    public void shutDown() {
        workingQueue.shutDown();
    }

    public Exception getException(Runnable task) {
        return workingQueue.getException(task);
    }

}
