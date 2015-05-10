package hw;

public class FixedThreadPool {

    private Thread[] threads;
    private ConcurrentQueue q = new ConcurrentQueue();
    private volatile boolean stopping;

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

    public void join() throws InterruptedException {
        while (!q.isEmpty()) {}

        stopping = true;

        for(Thread t:threads){
            t.join();
        }
    }

    private class FixedThreadPoolThread extends Thread {
        @Override
        public void run() {
            while (!stopping) {
                Future f = q.poll();
                if (f != null) {
                    f.run();
                }
            }
        }
    }
}
