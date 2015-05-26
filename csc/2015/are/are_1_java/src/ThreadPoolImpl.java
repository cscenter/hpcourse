import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

public class ThreadPoolImpl {

    private final ArrayList<Thread> threads;
    private final BlockingQueueImpl<Future<?>> blockingQueue;
    private static final int QUEUE_CAPACITY = 100;
    private volatile boolean interrupted = false;
    private final HashSet<Integer> tasks;

    public ThreadPoolImpl(int nThreads) {
        threads = new ArrayList<>(nThreads);
        for (int i = 0; i < nThreads; ++i) {
            threads.add(new Thread(new Worker()));
        }
        blockingQueue = new BlockingQueueImpl<>(QUEUE_CAPACITY);
        tasks = new HashSet<>();
    }

    public Future<?> submit(Callable<?> callable, int id) throws InterruptedException {
        Future<?> future = new FutureImpl<>(callable, id);
        blockingQueue.push(future);
        synchronized (tasks) {
            tasks.add(id);
        }
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
                    if (Thread.currentThread().isInterrupted()) break;
                    FutureImpl<?> task = (FutureImpl<?>) blockingQueue.pop();
                    task.run();
                    synchronized (tasks) {
                        tasks.remove(task.id);
                    }
                } catch (InterruptedException e) {
                    if (interrupted) return;
                    return;
                }
            }
        }
    }

    private enum States {
        NEW,
        IN_PROCESS,
        DONE,
        CANCELED,
        ERROR
    }

    public class FutureImpl<T> implements Future<T>, Runnable {

        private final Callable<T> task;
        private final AtomicReference<States> state;
        private volatile Thread runningThread;
        private volatile T result;
        private volatile Throwable ex;
        public final int id;

        public FutureImpl(Runnable task, T result, int id) {
            this(Executors.callable(task, result), id);
        }

        public FutureImpl(Callable<T> task, int id) {
            this.id = id;
            this.task = task;
            result = null;
            state = new AtomicReference<>(States.NEW);
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            if (state.get() == States.CANCELED) return true;
            if (state.compareAndSet(States.NEW, States.CANCELED)) {
                return true;
            } else if (mayInterruptIfRunning) {
                try {
                    while (runningThread == null) Thread.yield();
                    runningThread.interrupt();
                } finally {
                    state.set(States.CANCELED);
                }
                return true;
            }
            return false;
        }

        @Override
        public boolean isCancelled() {
            return state.get() == States.CANCELED;
        }

        @Override
        public boolean isDone() {
            return state.get() == States.DONE;
        }

        @Override
        public T get() throws InterruptedException, ExecutionException {
            synchronized (task) {
                while (state.get() != States.DONE && state.get() != States.CANCELED && state.get() != States.ERROR) {
                    task.wait();
                }
            }
            if (state.get() == States.CANCELED) throw new CancellationException();
            if (state.get() == States.ERROR) throw new ExecutionException("Error during completing task.", ex);
            return result;
        }

        @Override
        public T get(long timeout, @NotNull TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            long deadline = unit.toMillis(timeout) + System.currentTimeMillis();
            synchronized (task) {
                while (state.get() != States.DONE && state.get() != States.CANCELED && state.get() != States.ERROR) {
                    task.wait(unit.toMillis(timeout));
                    if (System.currentTimeMillis() >= deadline) break;
                }
            }
            if (state.get() == States.CANCELED) throw new CancellationException();
            if (state.get() == States.ERROR) throw new ExecutionException("Error during completing task.", ex);
            if (state.get() != States.DONE) throw new TimeoutException();
            return result;
        }

        @Override
        public void run() {
            if (state.compareAndSet(States.NEW, States.IN_PROCESS)) {
                runningThread = Thread.currentThread();
                try {
                    result = task.call();
                } catch (Throwable e) {
                    ex = e;
                    state.set(States.ERROR);
                    return;
                }

                state.compareAndSet(States.IN_PROCESS, States.DONE);

                synchronized (task) {
                    task.notifyAll();
                }
            }
        }

        @SuppressWarnings("unused")
        public void waitTask(int taskId) {
            while (true) {
                synchronized (tasks) {
                    if (!tasks.contains(taskId)) break;
                }
                try {
                    FutureImpl future = (FutureImpl)blockingQueue.pop();
                    future.run();
                    synchronized (tasks) {
                        tasks.remove(future.id);
                    }
                } catch (InterruptedException e) {
                    state.set(States.ERROR);
                }
            }
        }

        public States getStatus() {
            return state.get();
        }
    }
}
