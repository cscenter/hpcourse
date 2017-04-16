package ru.csc.concurrent;

public class ThreadPoolExecutor extends Thread {
    private final ThreadPool pool;
    private final ThreadPoolQueue queue;

    public ThreadPoolExecutor(ThreadPool pool) {
        this.pool = pool;
        this.queue = pool.registerThreadExecutor();
    }

    @Override
    public void run() {
        try {
            pool.runExecutor(queue);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
