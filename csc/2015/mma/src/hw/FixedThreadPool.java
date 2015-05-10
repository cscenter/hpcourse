package hw;


public class FixedThreadPool {
    /*private final Thread[] threads;
    private final ConcurrentQueue q = new ConcurrentQueue();

    public FixedThreadPool(int nThreads) {
        threads = new Thread[nThreads];
        for (int i = 0; i < nThreads; i++) {
            threads[i] = new FixedThreadPoolThread();
            threads[i].start();
        }
    }

    public void submit(Future future) {
        q.add(future);
    }

    public void join() {
        while (!q.isEmpty()) {
        }
    }

    private class FixedThreadPoolThread extends Thread {
        @Override
        public void run() {
            while (true) {
                Future future = q.poll();
                if (future != null) {
                    future.run();
                }
            }
        }
    }*/

    private Thread[] threads;
    private ConcurrentQueue q = new ConcurrentQueue();

    public FixedThreadPool(int nThreads) {
        threads = new Thread[nThreads];
        for (int i = 0; i < nThreads; i++) {
            threads[i] = new FixedThreadPoolThread();
            threads[i].start();
        }
    }

    public void submit(Future future) throws IndexOutOfBoundsException {
        q.add(future);
    }

    public void join() {
        while (!q.isEmpty()) {
        }
    }

    private class FixedThreadPoolThread extends Thread {
        @Override
        public void run() {
            while (true) {
                Future f = q.poll();
                if (f != null) {
                    f.run();
                }
            }
        }
    }
}
