package ru.csc2015.concurrency;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

public class FixedThreadPool {
    private final ArrayList<Thread> threads;
    private final ConcurrentQueue<MyFuture<?>> queue;
    private final HashSet<Long> tasks;

    public FixedThreadPool(int numOfThreads) {
        threads = new ArrayList<>(numOfThreads);

        for (int i = 0; i < numOfThreads; i++) {
            threads.add(new Thread(new Worker()));
        }

        queue = new ConcurrentQueue<>();
        tasks = new HashSet<>();
    }

    public MyFuture<?> submit(Callable<?> callable, long id) throws InterruptedException {
        MyFuture<?> future = new MyFuture<>(callable, id);

        queue.push(future);

        synchronized (tasks) {
            tasks.add(id);
        }

        return future;
    }

    public void start() {
        for (Thread thread : threads) {
            thread.start();
        }
    }

    public void exit() {
        for (Thread thread : threads) {
            thread.interrupt();
        }
    }

    private class Worker implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }

                    MyFuture<?> task = queue.pop();
                    task.run();

                    synchronized (tasks) {
                        tasks.remove(task.id);
                    }
                } catch (InterruptedException e) {
                    return;
                }
            }
        }
    }
}
