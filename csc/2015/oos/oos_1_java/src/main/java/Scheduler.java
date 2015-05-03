import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Future;

/**
 * Created by olgaoskina
 * 02 May 2015
 */
public class Scheduler {

    private final int threadCount;
    private final WorkerThread[] threads;
    private final List<TaskFuture<UUID>> futures = new ArrayList<>();

    public Scheduler(int threadCount) {
        this.threadCount = threadCount;
        threads = new WorkerThread[threadCount];

        for (WorkerThread thread : threads) {
            thread = new WorkerThread();
            thread.start();
        }
    }

    public TaskFuture<UUID> submit(Runnable task) {
        TaskFuture<UUID> taskFuture = new TaskFuture<>();
        taskFuture.setTask(task);
        synchronized (futures) {
            futures.add(taskFuture);
            futures.notify();
        }
        return taskFuture;
    }

    public class WorkerThread extends Thread {
        @Override
        public void run() {
            while (true) {
                TaskFuture<UUID> future;
                synchronized (futures) {
                    while (futures.isEmpty()) {
                        try {
                            futures.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    future = futures.remove(0);
                }
                future.setThread(this);
                // TODO: change status
                try {
                    future.getTask().run();
                } catch (RuntimeException e) {
                    // If we don't catch RuntimeException,
                    // the pool could leak threads
                }
            }
        }
    }
}
