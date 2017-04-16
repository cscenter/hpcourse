import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

public class SimpleFixedThreadPool implements ExecutorService { // based on ThreadPoolExecutor
    private volatile int state;

    private static final int RUNNING    = 1;
    private static final int TIDYING    = 2;
    private static final int TERMINATED = 3;

    private final BlockingQueue<Runnable> queue; // contains runnable futures representing tasks
    private final Thread[] workers; // worker threads pool

    private final ReentrantLock reentrantLock = new ReentrantLock();

    @Override
    public void shutdown() {
        throw new NotImplementedException();
    }

    @Override
    public List<Runnable> shutdownNow() {
        reentrantLock.lock();
        ArrayList<Runnable> result;
        try {
            state = TIDYING;
            for (Thread worker : workers) {
                worker.interrupt();
            }
            result = new ArrayList<>();
            queue.drainTo(result); // return unfinished tasks
            state = TERMINATED;
        } finally {
            reentrantLock.unlock();
        }
        return result;
    }

    @Override
    public boolean isShutdown() {
        return state == RUNNING;
    }

    @Override
    public boolean isTerminated() {
        return state == TERMINATED;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        throw new NotImplementedException();
    }

    @Override
    public <T> Future<T> submit(Callable<T> callable) {
        if (callable == null) {
            throw new NullPointerException();
        }
        RunnableFuture<T> rf = new SimpleRunnableFuture<>(callable);
        execute(rf);
        return rf;
    }

    @Override
    public <T> Future<T> submit(Runnable runnable, T result) {
        if (runnable == null) {
            throw new NullPointerException();
        }
        RunnableFuture<T> rf = new SimpleRunnableFuture<>(runnable, result);
        execute(rf);
        return rf;
    }

    @Override
    public Future<?> submit(Runnable runnable) {
        if (runnable == null) {
            throw new NullPointerException();
        }
        RunnableFuture<Void> rf = new SimpleRunnableFuture<>(runnable, null);
        execute(rf);
        return rf;
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        throw new NotImplementedException();
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
        throw new NotImplementedException();
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        throw new NotImplementedException();
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        throw new NotImplementedException();
    }

    @Override
    public void execute(Runnable command) {
        if (command == null) {
            throw new NullPointerException();
        }
        if (state == RUNNING) {
            try {
                queue.put(command);
            } catch (InterruptedException e) {
                ;
            }
        }
    }

    public Runnable getTask() throws InterruptedException {
        return queue.take();
    }

    public SimpleFixedThreadPool(int numOfThreads, int queueLimit) {
        if (numOfThreads <= 0) {
            throw new IllegalArgumentException();
        }
        queue = new SimpleBlockingQueue<>(queueLimit);
        workers = new Thread[numOfThreads];
        for (int i = 0; i < numOfThreads; i++) {
            workers[i] = new WorkerThread(this);
            workers[i].start();
        }
        state = RUNNING;
    }
}
