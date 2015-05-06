import org.jetbrains.annotations.NotNull;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

public class FutureImpl<T> implements Future<T>, Runnable {

    private final Callable<T> task;
    private final AtomicReference<States> state;
    private volatile Thread runningThread;
    private volatile T result;
    private volatile Throwable ex;

    private enum States {
        NEW,
        IN_PROCESS,
        DONE,
        CANCELED,
        ERROR
    }

    public FutureImpl(Runnable task, T result) {
        this(Executors.callable(task, result));
    }

    public FutureImpl(Callable<T> task) {
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

    public States getStatus() {
        return state.get();
    }
}
