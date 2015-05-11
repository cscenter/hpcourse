package ru.csc.concurrent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;


public class MyLittleThreadPool<T> {
    private final HashMap<Integer, LittleTask<T>> taskMap;
    private final SafeQueue safeQueue;
    private final int countThreads;
    private final List<LittleThread> threads = new ArrayList<>();
    private AtomicInteger countTasks;

    public MyLittleThreadPool(int countThreads) {
        this.countThreads = countThreads;
        this.taskMap = new HashMap<>();
        this.safeQueue = new LittleSafeQueue();
        this.countTasks = new AtomicInteger(0);
        for (int i = 0; i < this.countThreads; i++) {
            LittleThread thread = new LittleThread(safeQueue);
            threads.add(thread);
            thread.start();
        }
    }

    public Integer submit(Callable<T> task) {
        Integer taskID = countTasks.getAndIncrement();
        LittleTask<T> t = new LittleTask<>(task);
        taskMap.put(taskID, t);
        if (safeQueue.push(taskID)) {
            synchronized (safeQueue) {
                safeQueue.notifyAll();
            }
        }
        return taskID;
    }

    public LittleTask<T> getTask(Integer taskID) {
        if (taskMap.containsKey(taskID)) {
            return taskMap.get(taskID);
        }
        return null;
    }

    public class LittleThread extends Thread {
        private final SafeQueue workQueue;

        public LittleThread(SafeQueue workQueue) {
            this.workQueue = workQueue;
        }

        @Override
        public void run() {
            while (true) {
                try {
                    Integer taskID = workQueue.pop();
                    LittleTask<T> task = getTask(taskID);
                    if (task != null && !task.isDone()) {
                        task.get();
                    } else {
                        waitForNotification();
                    }
                } catch (InterruptedException e) {
                    //System.out.println(e.getMessage());
                } catch (ExecutionException e) {
                    //throw new RuntimeException(e);
                    System.out.println("Runtime exception");
                }
            }
        }

        private void waitForNotification() {
            synchronized (workQueue) {
                try {
                    workQueue.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}