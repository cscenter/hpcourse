package hw;

import java.util.LinkedList;
import java.util.Queue;

public class FixedThreadPool {

    private Thread[] threads;
    private IQueue<Future> q = new LockFreeQueue<Future>();
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
        while (true) {
            if(q.isEmpty()){
                break;
            }
        }

        stopping = true;

        for(Thread t:threads){
            t.join();
        }
    }

    private class FixedThreadPoolThread extends Thread {
        @Override
        public void run() {
            while (!stopping) {
                Future f = null;
                f = q.poll();
                if (f != null) {
                    f.run();
                }
            }
        }
    }
}
