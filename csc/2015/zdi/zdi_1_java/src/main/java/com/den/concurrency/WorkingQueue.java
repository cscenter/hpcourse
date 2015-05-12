package com.den.concurrency;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

/**
 * @author Zaycev Denis
 */
public class WorkingQueue {

    private final Worker[] workers;

    private final LinkedList<Runnable> tasks;

    private final ThreadLocalStorage<LinkedList<Runnable>> threadTasksStorage
            = new ThreadLocalStorage<LinkedList<Runnable>>() {

        @Override
        public LinkedList<Runnable> getInitialValue() {
            return new LinkedList<Runnable>();
        }

    };

    private final Map<Runnable, Exception> taskException = new HashMap<Runnable, Exception>();

    private boolean alive = true;

    public WorkingQueue(int maxThreadsCount) {
        this.tasks = new LinkedList<Runnable>();

        workers = new Worker[maxThreadsCount];
        for (int i = 0; i < maxThreadsCount; i++) {
            workers[i] = new Worker();
            workers[i].start();
        }
    }

    public void execute(Runnable task) {
        if (!alive) {
            throw new IllegalStateException("Trying to submit new task after shut down!");
        }

        Thread current = Thread.currentThread();
        if (current instanceof Worker) {
            threadTasksStorage.get().addLast(task);
        } else {

            synchronized (tasks) {
                tasks.addLast(task);
                tasks.notify();
            }

        }
    }

    public Exception getException(Runnable task) {
        synchronized (taskException) {
            return taskException.get(task);
        }
    }

    public synchronized void shutDown() {
        if (alive) {

            alive = false;
            for (Worker worker : workers) {
                worker.interrupt();
            }

        }
    }

    private void addException(Runnable task, Exception e) {
        synchronized (taskException) {
            taskException.put(task, e);
        }
    }

    public class Worker extends Thread {

        @Override
        public void run() {
            while (alive) {
                runNextTask();
            }
        }

        public void runNextTask() {
            Runnable task = getTaskFromAssigned();
            if (task == null) {
                task = getTaskFromSharedQueue();
            }

            if (task == null) {
                // means that
                // queue is not alive
                return;
            }

            try {
                task.run();
            } catch (Exception e) {
                addException(task, e);
            }
        }

        private Runnable getTaskFromAssigned() {
            LinkedList<Runnable> assigned = threadTasksStorage.get();
            if (assigned.isEmpty()) {
                return null;
            }

            return alive ? assigned.removeFirst() : null;
        }

        private Runnable getTaskFromSharedQueue() {
            synchronized (tasks) {
                while (tasks.isEmpty() && alive) {
                    try {
                        tasks.wait();
                    } catch (InterruptedException e) { /* JUST IGNORE */ }
                }

                if (!alive) {
                    return null;
                }

                return tasks.removeFirst();
            }
        }

    }

}
