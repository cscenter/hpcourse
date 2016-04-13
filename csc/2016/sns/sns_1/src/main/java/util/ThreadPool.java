package util;

import java.util.Deque;
import java.util.LinkedList;

/**
 * Created by nikita.sokeran@gmail.com
 */
public class ThreadPool {

    /**
     * Not for concurrency environment
     */
    final Deque<PoolThread> threads = new LinkedList<>();

    final Deque<Runnable> tasks = new LinkedList<>();

    public ThreadPool() {
        final int parallelism = Runtime.getRuntime().availableProcessors();
        for (int i = 0; i < parallelism; i++) {
            final PoolThread poolThread = new PoolThread();
            threads.add(poolThread);
            poolThread.start();
        }
    }

    public void execute(final Runnable task) {
        synchronized (tasks) {
            tasks.add(task);
            tasks.notify();
        }
    }

    private class PoolThread extends Thread {
        @Override
        public void run() {
            Runnable task;

            while (true) {
                synchronized (tasks) {
                    while (tasks.isEmpty()) {
                        try {
                            tasks.wait();
                        } catch (InterruptedException ignored) {

                        }
                    }

                    task = tasks.remove();
                }

                try {
                    task.run();
                } catch (RuntimeException e) {
                    System.out.println("Error occured in thread worker:" + e);
                }
            }
        }
    }


}
