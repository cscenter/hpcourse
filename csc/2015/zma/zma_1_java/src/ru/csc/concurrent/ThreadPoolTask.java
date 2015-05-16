package ru.csc.concurrent;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ThreadPoolTask<T> implements Future<T> {
    public enum Status {
        WAITING, DONE, CANCELLED, RUNNING, EXECUTION_EXCEPTION
    }

    private final Runnable runnable;
    private final int taskId;

    private volatile Status status;
    private volatile Thread executor = null;
    private final Object resultingLock = new Object();

    public ThreadPoolTask(Runnable runnable, int taskId) {
        this.runnable = runnable;
        this.taskId = taskId;
        this.status = Status.WAITING;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        status = Status.CANCELLED;
        if (mayInterruptIfRunning) {
            if (executor != null) {
                executor.interrupt();
            }
        }

        return !isDone();
    }

    @Override
    public boolean isCancelled() {
        return status == Status.CANCELLED;
    }

    @Override
    public boolean isDone() {
        return status == Status.DONE;
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
        if (isCancelled()) {
            throw new InterruptedException();
        }
        while (!isCancelled() && !isDone()) {
            synchronized (resultingLock) {
                resultingLock.wait(100);
            }
        }

        return null;
    }

    @Override
    public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        throw new UnsupportedOperationException("get with timeout is not supported for now");
    }

    public int getTaskId() {
        return taskId;
    }

    public Status getStatus() {
        return status;
    }

    boolean execute() {
        try {
            if (isCancelled()) {
                return true;
            }

            executor = Thread.currentThread();
            status = Status.RUNNING;
            runnable.run();
        } catch (Throwable e) {
            status = Status.EXECUTION_EXCEPTION;
        } finally {
            executor = null;
            synchronized (resultingLock) {
                resultingLock.notifyAll();
            }
        }

        if (status == Status.RUNNING) {
            status = Status.DONE;
        }

        return true;
    }

    public static Runnable createTimedTask(int seconds) {
        return () -> {
            try {
                Thread.sleep(seconds * 1000);
                System.out.println("Task is done");
            } catch (InterruptedException e) {
                System.out.println("Task has been interrupted");
            }
        };
    }
}
