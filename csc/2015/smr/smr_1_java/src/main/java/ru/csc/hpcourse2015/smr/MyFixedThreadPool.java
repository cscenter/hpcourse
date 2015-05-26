package ru.csc.hpcourse2015.smr;


import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.FileHandler;

public class MyFixedThreadPool {

    private AtomicBoolean isWorking;

    private final Integer numberThreads;
    private final Queue<FutureTask> queue;
    private final List<MyThread> threads;
    private final Map<Integer, FutureTask> tasks;

   // private final Object lockTasks = new Object();

    public MyFixedThreadPool(Integer numberThreads) {
        if (numberThreads <= 0)
            throw new IllegalArgumentException();

        this.numberThreads = numberThreads;
        isWorking = new AtomicBoolean(true);
        queue = new LinkedList<>();
        threads = new ArrayList<>();
        tasks = new HashMap<>();
        for (int i = 0; i < this.numberThreads; i++) {
            MyThread thread = new MyThread();
            threads.add(thread);
            thread.start();
        }
    }

    public FutureTask submit(Callable callable, Integer id) {
        FutureTask task = new FutureTask(callable);
        addLock(task, id);
        return task;
    }

    private void addLock(FutureTask task, Integer id) {
        synchronized (queue) {
            queue.add(task);
            tasks.put(id, task);
            queue.notify();
        }
    }

    private FutureTask popLock() {
        synchronized (queue) {
            if (!queue.isEmpty()) {
                return queue.poll();
            }
            return null;
        }
    }

    public String status(Integer id) {
        if (!tasks.containsKey(id)) {
            return "No Task with this id!";
        }
        FutureTask task = tasks.get(id);
        return task.getStatus();
    }

    public boolean cancel(Integer id) {
        if (!tasks.containsKey(id))
            throw new IllegalArgumentException();
        FutureTask task = tasks.get(id);
        tasks.remove(id);
        return task.cancel(true);
    }

    public void exit() {
        for (MyThread thread : threads) {
            thread.interrupt();
        }
        isWorking.compareAndSet(true, false);
    }

    private class MyThread extends Thread {

        @Override
        public void run() {
            while (isWorking.get()) {
                synchronized (queue) {
                    if (queue.isEmpty()) {
                        try {
                            queue.wait();
                        } catch (InterruptedException e) {
                            break;
                        }
                    }
                }
                Thread.currentThread().interrupted();
                FutureTask task = popLock();

                if (task != null) {
                    task.start();
                }
            }
        }
    }
}
