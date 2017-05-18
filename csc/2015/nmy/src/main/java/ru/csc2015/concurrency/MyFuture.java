package ru.csc2015.concurrency;

import java.util.concurrent.*;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicReference;

public class MyFuture<T> implements Future<T>, Runnable {
    private final Callable<T> task;
    private final AtomicReference<Status> state;
    private volatile Thread activeThread;
    private volatile Throwable throwable;
    private volatile T result;
    public final long id;

    public MyFuture(Callable<T> task, long id) {
        this.id = id;
        this.task = task;
        result = null;
        state = new AtomicReference<>(Status.READY);
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        if ((state.get() == Status.CANCELED) || (state.compareAndSet(Status.READY, Status.CANCELED))) {
            return true;
        } else if (mayInterruptIfRunning) {
            try {
                while (activeThread == null) {
                    Thread.yield();
                }

                activeThread.interrupt();
            } finally {
                state.set(Status.CANCELED);
            }
            return true;
        }

        return false;
    }

    @Override
    public boolean isCancelled() {
        return state.get() == Status.CANCELED;
    }

    @Override
    public boolean isDone() {
        return state.get() == Status.DONE;
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
        synchronized (task) {
            while (state.get() == Status.READY || state.get() == Status.RUNNING) {
                task.wait();
            }
        }

        if (state.get() == Status.CANCELED) {
            throw new CancellationException();
        }

        if (state.get() == Status.ERROR) {
            throw new ExecutionException("Error during completing task.", throwable);
        }

        return result;
    }

    @Override
    public T get(long timeout, @NotNull TimeUnit unit) {
        throw new UnsupportedOperationException("Operation not implemented");
    }

    @Override
    public void run() {
        if (state.compareAndSet(Status.READY, Status.RUNNING)) {
            activeThread = Thread.currentThread();

            try {
                result = task.call();
            } catch (Throwable e) {
                throwable = e;
                state.set(Status.ERROR);
                return;
            }

            state.compareAndSet(Status.RUNNING, Status.DONE);

            synchronized (task) {
                task.notifyAll();
            }
        }
    }

    public Status getStatus() {
        return state.get();
    }

    private enum Status {
        READY("ready"),
        RUNNING("running"),
        DONE("done"),
        CANCELED("canceled"),
        ERROR("error");

        private final String status;

        Status(String status) {
            this.status = status;
        }

        public String getStatus() {
            return status;
        }
    }
}