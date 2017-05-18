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

    public void awaitCompletion() throws InterruptedException {
        stopping = true;
        synchronized (queue){
            queue.notifyAll();
        }
        for (Thread t : threads) {
            t.join();
        }
    }

    public void submit(Future r) {
        if (stopping) {
            throw new IllegalStateException("stopping");
        }
        synchronized (queue) {
            queue.addLast(r);
            queue.notify();
        }
    }

    public class FixedThreadPoolThread extends Thread {
        public void run() {
            while (true) {
                Future r;
                synchronized (queue) {
                    while (queue.isEmpty() && !stopping) {
                        try {
                            queue.wait();
                        } catch (InterruptedException e) {
                        }
                    }
                    r = (Future) queue.poll();
                }

                if (r == null && stopping) {
                    break;
                }

                try {
                    r.get();
                } catch (Exception e) {
                }
            }
        }
    }
}
