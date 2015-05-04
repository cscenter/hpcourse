import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

public class ThreadPoolImpl {

    private final ArrayList<Thread> threads;
    private final BlockingQueueImpl<Future<?>> blockingQueue;
    private static final int QUEUE_CAPACITY = 100;
    private volatile boolean interrupted = false;

    public ThreadPoolImpl(int nThreads) {
        threads = new ArrayList<>(nThreads);
        for (int i = 0; i < nThreads; ++i) {
            threads.add(new Thread(new Worker()));
        }
        blockingQueue = new BlockingQueueImpl<>(QUEUE_CAPACITY);
    }

    public Future<?> submit(Callable<?> callable) throws InterruptedException {
        Future<?> future = new FutureImpl<>(callable);
        blockingQueue.push(future);
        return future;
    }

    public void execute() {
        threads.forEach(java.lang.Thread::start);
    }

    public void interrupt() {
        interrupted = true;
        threads.forEach(Thread::interrupt);
    }

    private class Worker implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    if(Thread.currentThread().isInterrupted()) break;
                    FutureImpl<?> task = (FutureImpl<?>) blockingQueue.pop();
                    task.run();
                } catch (InterruptedException e) {
                    if (interrupted) return;
                    return;
                }
            }
        }
    }
}
