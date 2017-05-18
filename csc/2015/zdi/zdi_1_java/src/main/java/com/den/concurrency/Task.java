package com.den.concurrency;

import java.util.concurrent.Callable;

/**
 * @author Zaycev Denis
 */
public class Task<T> implements Runnable {

    public static enum TaskState {
        READY,
        IN_PROCESS,
        TERMINATED,
        FINISHED_WITH_ERROR,
        FINISHED_SUCCESSFULLY
    }

    private T result;

    private TaskState state;

    private Callable<T> task;

    private Thread runner;

    public Task(Callable<T> task) {
        this.task = task;
        this.state = TaskState.READY;
    }

    public synchronized T get() throws InterruptedException {
        Thread current = Thread.currentThread();
        if (current instanceof WorkingQueue.Worker) {

            WorkingQueue.Worker worker = (WorkingQueue.Worker) current;
            while (result == null) {
                worker.runNextTask();
            }

        } else {
            wait();
        }

        return result;
    }

    public TaskState getState() {
        return state;
    }

    public synchronized void terminate() throws Exception {
        if (!setTerminated()) {
            throw new Exception("Task in state " + state + " could not be terminated!");
        }

        notifyAll();

        runner.interrupt();
    }

    @Override
    public void run() {
        if (!setInProcess()) {
            throw new IllegalStateException("Task is already in process!");
        }

        try {

            runner = Thread.currentThread();

            result = task.call();

            state = TaskState.FINISHED_SUCCESSFULLY;

        } catch (Exception e) {
            if (!TaskState.TERMINATED.equals(state)) {
                state = TaskState.FINISHED_WITH_ERROR;
                throw new IllegalStateException("Exception while task processing happened: ", e);
            }

        } finally {
            synchronized (this) {
                notifyAll();
            }

        }
    }

    private synchronized boolean setTerminated() {
        if (!TaskState.READY.equals(state) && !TaskState.IN_PROCESS.equals(state)) {
            return false;
        }

        state = TaskState.TERMINATED;

        return true;
    }

    private synchronized boolean setInProcess() {
        if (!TaskState.READY.equals(state)) {
            return false;
        }

        state = TaskState.IN_PROCESS;

        return true;
    }

}
