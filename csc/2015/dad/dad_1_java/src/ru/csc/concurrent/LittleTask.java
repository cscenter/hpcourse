package ru.csc.concurrent;

import java.util.concurrent.*;

public class LittleTask<T> implements Future<T> {
    private final Callable<T> callable;
    private boolean cancelled;
    private boolean done;
    private boolean running;
    private Thread thread;

    public LittleTask(Callable<T> callable) {
        this.callable = callable;
        this.cancelled = false;
        this.done = false;
        this.running = false;
        this.thread = null;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        if (isDone()) {
            return false;
        }

        if (mayInterruptIfRunning && isRunning()){
            this.thread.interrupt();
        }

        this.cancelled = true;
        return true;
    }

    private boolean isRunning() {
        return this.running && !this.done;
    }

    @Override
    public boolean isCancelled() {
        return this.cancelled;
    }

    @Override
    public boolean isDone() {
        return isCancelled() || this.done;
    }

    public String status() {
        if (isCancelled()) {
            return "Is cancelled";
        }
        if (isDone()) {
            return "Is done";
        }
        if (isRunning()){
            return "Is running";
        }
        return "Is waiting";
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
        this.thread = Thread.currentThread();
        this.running = true;
        if (isCancelled()) {
            throw new InterruptedException("Task has been cancelled");
            //return null;
        }
        try {
            callable.call();
        }
        catch (Exception e) {
            if (!(e instanceof InterruptedException)) {
                throw new ExecutionException(e);
            } else {
                throw new InterruptedException("Task has been cancelled");
            }
        }
        this.done = true;
        return null;
    }

    @Override
    public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        throw new UnsupportedOperationException();
    }

    public static Callable<Void> createTimedTask(final int seconds) {
        return new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                //System.out.println(seconds);
                Thread.sleep(1000);
                if (seconds > 1) {
                    LittleTask<Void> t = new LittleTask<>(createTimedTask(seconds - 1));
                    try {
                        t.get();
                    } catch (InterruptedException e) {
                        throw new InterruptedException(e.getMessage());
                    } catch (ExecutionException e) {
                        throw new ExecutionException(e);
                    }
                }
                return null;
            }
        };
    }
}