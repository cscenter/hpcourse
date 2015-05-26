package ru.compscicenter.ThreadPool;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Created by Flok on 01.05.2015.
 */
public class TaskFuture<V> implements Future<V>, InterruptionMonitor {
    private final Task<V> task;
    private volatile boolean isDone = false;
    private volatile Boolean isCanceled = false;
    private volatile Boolean run = false;
    private volatile boolean isInterrupted = false;
    Thread executionThread;
    private Exception executionException = null;

    TaskFuture(Task task) {
        this.task = task;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        isCanceled = true;
        if (isDone) {
            return false;
        }

        if (!mayInterruptIfRunning && run) {
            return false;
        }

        if (mayInterruptIfRunning && executionThread != null) {
            LivingThread livingThread = (LivingThread) executionThread;
            synchronized (livingThread.currentTaskMonitor) {
                isInterrupted = true;
                if (livingThread.currentTask == this) {
                    livingThread.interrupt();
                }
            }
        }

        return true;
    }

    @Override
    public boolean isCancelled() {
        return isCanceled;
    }

    @Override
    public boolean isDone() {
        return isDone || isCanceled;
    }

    public boolean isInterrupted() {
        return isInterrupted;
    }

    public boolean hasException() {
        return executionException != null;
    }

    public Exception getException() {
        return executionException;
    }

    @Override
    public V get() {
        while (true) {
            if (isDone()) {
                return task.getResult();
            } else {
                Thread thread = Thread.currentThread();
                if (thread instanceof LivingThread) {
                    LivingThread livingThread = (LivingThread) thread;
                    livingThread.doWork(this);
                } else {
                    synchronized (this) {
                        try {
                            wait();
                        } catch (InterruptedException e) {

                        }
                    }
                }
            }
        }
    }

    @Override
    public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        throw new UnsupportedOperationException();
    }

    void run() {
        if (!isCanceled) {
            run = true;
            executionThread = Thread.currentThread();
            try {
                task.run();
            } catch (Exception e) {
                executionException = e;
            }
            isDone = true;
        }
    }
}
