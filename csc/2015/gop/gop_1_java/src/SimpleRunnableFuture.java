import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class SimpleRunnableFuture<V> implements RunnableFuture<V> { // based on FutureTask
    private volatile Callable<V> target;

    private V result;
    private Throwable throwable;

    private volatile Thread runner;

    private AtomicInteger state;

    private static final int NEW          = 1;
    private static final int COMPLETING   = 2;
    private static final int NORMAL       = 3;
    private static final int EXCEPTIONAL  = 4;
    private static final int CANCELLED    = 5;
    private static final int INTERRUPTING = 6;
    private static final int INTERRUPTED  = 7;

    @Override
    public void run() {
        if (!state.compareAndSet(NEW, COMPLETING)) return; // prevent concurrent calls to run()
        runner = Thread.currentThread();
        try {
            synchronized (target) {
                try {
                    state.set(COMPLETING);
                    result = target.call();
                    state.set(NORMAL);
                } catch (Throwable t) {
                    throwable = t;
                    state.set(EXCEPTIONAL);
                }
                Callable<V> callable = target;
                if (callable != null) {
                    target.notify(); // wake up for waitForResult()
                }
            }
        } finally {
            runner = null;
        }
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        if (state.get() == NEW) {
            return false;
        }
        if (!state.compareAndSet(COMPLETING, mayInterruptIfRunning ? INTERRUPTING : CANCELLED)) { // try to set new status
            return false; // something went wrong
        }
        try { // in case exception comes out of interrupt()
            if (mayInterruptIfRunning) {
                try {
                    Thread thread = runner;
                    if (thread != null) {
                        thread.interrupt();
                    }
                } finally {
                    state.set(INTERRUPTED);
                }
            }
        } finally {
            target = null;
        }
        return true;
    }

    @Override
    public boolean isCancelled() {
        int s = state.get();
        return s == CANCELLED || s == INTERRUPTING || s == INTERRUPTED;
    }

    @Override
    public boolean isDone() {
        int s = state.get();
        return s != NEW && s != COMPLETING;
    }

    private void waitForResult() throws InterruptedException {
        synchronized (target) {
            int s = state.get();
            while (s != NEW && s != COMPLETING && s != INTERRUPTING) {
                target.wait();
                s = state.get();
            }
        }
    }

    private V getResult() throws ExecutionException, CancellationException {
        target = null;
        int s = state.get();
        if (s == NORMAL) {
            return result;
        }
        if (s == EXCEPTIONAL) {
            throw new ExecutionException(throwable);
        }
        throw new CancellationException();
    }

    @Override
    public V get() throws InterruptedException, ExecutionException {
        int s = state.get();
        if (s == COMPLETING || s == NEW) {
            waitForResult();
        }
        return getResult();
    }

    @Override
    public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        throw new NotImplementedException();
    }

    public SimpleRunnableFuture(Callable<V> callable) {
        if (callable == null) {
            throw new NullPointerException();
        }
        target = callable;
        state = new AtomicInteger(NEW);
    }

    public SimpleRunnableFuture(Runnable runnable, V result) {
        if (runnable == null) {
            throw new NullPointerException();
        }
        target = Executors.callable(runnable, result);
        state = new AtomicInteger(NEW);
    }
}
