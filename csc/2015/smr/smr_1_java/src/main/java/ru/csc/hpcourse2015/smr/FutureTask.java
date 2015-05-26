package ru.csc.hpcourse2015.smr;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class FutureTask<T> implements Future<T> {

    private final Callable<T> task;
    private final AtomicReference<Status> taskStatus;

    private T result;
    private volatile Thread currentThread;
    private Throwable exception;


    public FutureTask(Callable<T> task) {
        if (task == null)
            throw new NullPointerException();
        this.task = task;
        result = null;
        this.taskStatus = new AtomicReference<>(Status.READY);
    }

    public FutureTask(Runnable task, T result) {
        this(Executors.callable(task, result));
    }



//    public static Callable<Void> createTask(final Integer duration) {
//        return new Callable<Void>() {
//            @Override
//            public Void call() throws Exception {
//                try {
//                    Thread.sleep(duration * 1000);
//                    System.out.println("Task creates successfully!");
//                } catch (InterruptedException exc) {
//                    throw new InterruptedException("Task interrupted.\n" + exc.getMessage());
//                }
//                return null;
//            }
//        };
//
//    }

    public String getStatus() {
        return taskStatus.get().getStatus();
    }

    public Callable getTask() {
        return task;
    }

    public void setThread(Thread thread) {
        this.currentThread = thread;
    }

    public void setTaskStatus(Status status) {
        this.taskStatus.set(status);
    }

    public void setException(Throwable exception) {
        this.exception = exception;
    }

    public void start() {
        taskStatus.compareAndSet(Status.READY, Status.RUNNING);
        currentThread = Thread.currentThread();
        try {
            result = task.call();
        } catch (Throwable e) {
            taskStatus.compareAndSet(Status.RUNNING, Status.ERROR);
            exception = e;
        }
        taskStatus.compareAndSet(Status.RUNNING, Status.DONE);
        synchronized (task) {
            task.notify();
        }
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        if (isDone())
            return false;
        if (isCancelled())
            return true;
        if (taskStatus.compareAndSet(Status.READY, Status.CANCELED))
            return true;
        if (mayInterruptIfRunning) {
            try {
                while (currentThread == null)
                    Thread.yield();
                currentThread.interrupt();
            } finally {
                taskStatus.set(Status.CANCELED);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean isCancelled() {
        return taskStatus.get() == Status.CANCELED;
    }

    @Override
    public boolean isDone() {
        return taskStatus.get() == Status.DONE;
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
        synchronized (task) {
            while (taskStatus.get() != Status.CANCELED && taskStatus.get() != Status.DONE && taskStatus.get() != Status.ERROR)
                task.wait();
        }
        if (taskStatus.get() == Status.CANCELED)
            throw new InterruptedException();
        if (taskStatus.get() == Status.ERROR)
            throw new ExecutionException("Error completing task.", exception);
        return result;
    }

    @Override
    public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        long end = unit.toMillis(timeout) + System.currentTimeMillis();
        synchronized (task) {
            while (taskStatus.get() != Status.CANCELED && taskStatus.get() != Status.DONE && taskStatus.get() != Status.ERROR) {
                task.wait(unit.toMillis(timeout));
                if (System.currentTimeMillis() >= end)
                    break;
            }
        }
        if (taskStatus.get() == Status.CANCELED)
            throw new InterruptedException();
        if (taskStatus.get() == Status.ERROR)
            throw new ExecutionException("Error completing task.", exception);
        if (taskStatus.get() != Status.DONE)
            throw new TimeoutException();
        return result;
    }

//    @Override
//    public void run() {
//        if (!taskStatus.compareAndSet(Status.READY, Status.RUNNING))
//            return;
//
//        currentThread = Thread.currentThread();
//
//        try {
//            result = task.call();
//        } catch (Throwable e) {
//            exception = e;
//            taskStatus.set(Status.ERROR);
//            return;
//        }
//        taskStatus.compareAndSet(Status.RUNNING, Status.DONE);
//
//        synchronized (task) {
//            task.notifyAll();
//        }
//    }

    public enum Status {
        READY("ready"),
        RUNNING("running"),
        DONE("done"),
        CANCELED("canceled"),
        ERROR("error");

        private final String status;

        private Status(String status) {
            this.status = status;
        }

        public String getStatus() {
            return status;
        }
    }

}

