package concurrent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

/**
 * Created by dkorolev on 4/9/2016.
 */
public class MyExecutorService {
    Queue<AsyncResult> queue;
    volatile boolean running;
    List<Thread> threads;

    MyExecutorService(int threadsCount) {
        running = true;
        queue = new ArrayDeque<>();
        threads = new ArrayList<>(threadsCount);
        for (int i = 0; i < threadsCount; i++) {
            Thread thread = new Thread(() -> {
                try {
                    while (running) {
                        AsyncResult callable;
                        synchronized (queue) {
                            if (queue.size() == 0)
                                queue.wait();
                            callable = queue.poll();
                        }
                        if (callable != null) {
                            try {
                                Object result = callable.getAction().call();
                                callable.setResult(result);
                            } catch (Exception e) {
                                callable.setResult(e);
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            });
            thread.start();
            threads.add(thread);
        }
    }

    public <T> AsyncResult<T> submit(MyCallable<T> action) {
        AsyncResult<T> asyncResult = new AsyncResult<>(action);
        synchronized (queue) {
            queue.add(asyncResult);
            queue.notify();
        }
        return asyncResult;
    }

    public void awaitTerminationSkipQueried() throws InterruptedException {
        running = false;
        synchronized (queue) {
            queue.notifyAll();
        }
        for (Thread thread : threads) {
            thread.join();
        }
    }

    public static MyExecutorService newFixedThreadPool(int threadsCount) {
        return new MyExecutorService(threadsCount);
    }

    public static MyExecutorService newSingleThreadExecutor() {
        return new MyExecutorService(1);
    }
}
