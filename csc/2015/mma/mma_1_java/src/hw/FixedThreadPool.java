package hw;

import java.util.LinkedList;

public class FixedThreadPool implements ThreadPool {
    private final LinkedList queue;
    private final Thread[] threads;

    private volatile boolean stopping;

    public FixedThreadPool(int nThreads) {
        queue = new LinkedList();
        threads = new FixedThreadPoolThread[nThreads];

        for (int i = 0; i < nThreads; i++) {
            threads[i] = new FixedThreadPoolThread();
            threads[i].start();
        }
    }

    public void awaitAll() throws InterruptedException {
        while (true) {
            if (queue.isEmpty()) {
                break;
            }
        }

        stopping = true;

        synchronized (queue) {
            queue.notifyAll();
        }

        for (Thread t : threads) {
            t.join();
        }
    }

    public void submit(Runnable r) {
        synchronized (queue) {
            queue.addLast(r);
            queue.notify();
        }
    }

    private class FixedThreadPoolThread extends Thread {
        public void run() {
            while (true) {
                Runnable r;
                synchronized (queue) {
                    while (queue.isEmpty() && !stopping) {
                        try {
                            queue.wait();
                        } catch (InterruptedException e) {
                        }
                    }
                    r = (Runnable) queue.poll();
                }
                if (stopping) {
                    break;
                }

                r.run();
            }
        }
    }
}
