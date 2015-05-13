package ru.csc.concurrent;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

public class ThreadPool {
    private final int threadCount;
    private final List<ThreadPoolQueue> queues; // Define queues here for task stealing
    private final ThreadPoolQueue runningTasks = new ThreadPoolQueue();
    private final List<ThreadPoolExecutor> executors = new ArrayList<>(); // Safe strong reference
    private AtomicInteger ids = new AtomicInteger();

    public ThreadPool(int threadCount) {
        this.threadCount = threadCount;
        this.queues = new ArrayList<>(threadCount);
        for (int i = 0; i < threadCount; i++) {
            ThreadPoolExecutor executor = new ThreadPoolExecutor(this);
            executors.add(executor);
            executor.start();
        }
    }

    ThreadPoolQueue registerThreadExecutor() {
        ThreadPoolQueue queue = new ThreadPoolQueue();
        queues.add(queue);
        return queue;
    }

    public synchronized int submit(Runnable runnable) {
        ThreadPoolTask<Void> task = new ThreadPoolTask<>(runnable, ids.get());
        int queueNumber = new Random().nextInt(threadCount);
        queues.get(queueNumber).push(task);
        synchronized (this) {
            notifyAll();
        }

        return ids.getAndIncrement();
    }

    public void interrupt(int taskId) {
        ThreadPoolTask<?> task = getTaskById(taskId);
        task.cancel(true);
    }

    public ThreadPoolTask<?> getTaskById(int taskId) {
        for (ThreadPoolQueue queue : queues) {
            ThreadPoolTask<?> task = queue.getTask(taskId);
            if (task != null) {
                return task;
            }
        }

        return runningTasks.getTask(taskId);
    }

//    Try to execute task in the passed queue. If there is no task available,
//    steal task from another queue
    void runExecutor(ThreadPoolQueue queue) throws InterruptedException {
        try {
            while (true) {
                ThreadPoolTask<?> task = queue.popIfExists();
                if (task == null) {
                    for (ThreadPoolQueue threadPoolQueue : queues) {
                        task = threadPoolQueue.popIfExists();
                        if (task != null) {
                            break;
                        }
                    }
                }
                if (task != null) {
                    runningTasks.push(task);
                    task.execute();
                    continue;
                }

                waitForNotification();
            }
        } catch (InterruptedException e) {
            throw new InterruptedException();
        }
    }

    private void waitForNotification() throws InterruptedException {
        synchronized (this) {
            this.wait(100);
        }
    }
}
