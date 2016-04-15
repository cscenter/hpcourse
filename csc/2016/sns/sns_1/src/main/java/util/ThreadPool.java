package util;

import java.util.Deque;
import java.util.LinkedList;
import java.util.logging.Logger;

/**
 * Created by nikita.sokeran@gmail.com
 */
public class ThreadPool {
    private static final Logger LOGGER = Logger.getLogger(ThreadPool.class.getName());

    final Deque<PoolThread> threads = new LinkedList<>();

    final Deque<Runnable> tasks = new LinkedList<>();

    @SuppressWarnings("CallToThreadStartDuringObjectConstruction")
    public ThreadPool() {
        final int parallelism = Runtime.getRuntime().availableProcessors();
        for (int i = 0; i < parallelism; i++) {
            final PoolThread poolThread = new PoolThread();
            threads.add(poolThread);
            poolThread.start();
        }
    }

    @SuppressWarnings("CallToNotifyInsteadOfNotifyAll")
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

            mainWhile: while (!interrupted()) {
                synchronized (tasks) {
                    while (tasks.isEmpty()) {
                        try {
                            tasks.wait();
                        } catch (InterruptedException ignored) {
                            continue mainWhile;
                        }
                    }
                    task = tasks.remove();
                }

                try {
                    task.run();
                } catch (Exception e) {
                    LOGGER.warning("Error occurred in thread worker:" + e);
                }
            }
        }
    }
}
