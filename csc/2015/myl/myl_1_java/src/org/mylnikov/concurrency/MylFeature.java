package org.mylnikov.concurrency;

import com.sun.istack.internal.NotNull;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by alex on 5/13/2015.
 */
public class MylFeature<V> implements Future<V> {
    private final Callable<V> task;
    private Thread currentThread;
    private V result = null;
    private Throwable exception = null; /*volodia Putin |<3  */
    private final AtomicLong state = new AtomicLong(NEW);

    private static final int NEW = 0;
    private static final int RUNNING = 1;
    private static final int CANCELED = 2;
    private static final int DONE = 3;
    private static final int ERROR = 4;

    public MylFeature(@NotNull Callable<V> task) {
        this.task = task;
    }

    public void start() {
        synchronized (state) {
            state.compareAndSet(NEW, RUNNING);
        }
        currentThread = Thread.currentThread();
        try {
            result = task.call();
        } catch (Throwable e) {
            synchronized (state) {
                state.compareAndSet(RUNNING, ERROR);
            }
            exception = e;
        }
        state.compareAndSet(RUNNING, DONE);

    }


    @Override
    public boolean cancel(boolean possibleToICansel) {
        if (state.get() == DONE)
            return false;
        if (state.get() == CANCELED)
            return true;
        if (state.compareAndSet(NEW, CANCELED))
            return true;
        if (possibleToICansel) {
            try {
                Thread t;
                while ((t = currentThread) == null) {
                    Thread.yield();
                }
                if (t != null) {
                    t.interrupt();
                }
            } finally {
                state.set(CANCELED);
            }
        }
        return false;
    }


    @Override
    public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {

        long deadline = unit.toMillis(timeout) + System.currentTimeMillis();
        synchronized (task) {
            while (System.currentTimeMillis() < deadline && (state.get() == NEW || state.get() == RUNNING)) {
                task.wait(unit.toMillis(timeout));
            }
        }
        if (state.get() != DONE)
            throw new TimeoutException();
        if (state.get() == ERROR)
            throw new ExecutionException(exception);
        if (state.get() == CANCELED)
            throw new CancellationException();

        return result;
    }


    @Override
    public boolean isCancelled() {
        if (state.get() == CANCELED)
            return true;
        return false;
    }

    @Override
    public boolean isDone() {
        if (state.get() == DONE)
            return true;
        else
            return false;
    }

    @Override
    public V get() throws InterruptedException, ExecutionException {
        synchronized (task) {
            while (state.get() == NEW || state.get() == RUNNING) {
                task.wait();
            }
        }
        if (state.get() == CANCELED)
            throw new CancellationException();
        if (state.get() == ERROR)
            throw new ExecutionException(exception);


        return result;
    }


    public String getStringStatusOfFea() {
        switch ((int) state.get()) {
            case NEW:
                return "n";
            case ERROR:
                return "err";
            case RUNNING:
                return "run";
            case CANCELED:
                return "can";
            case DONE:
                return "done";
            default:
                return "no such state";
        }

    }

}