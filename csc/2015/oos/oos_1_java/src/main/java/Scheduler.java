import java.util.*;

/**
 * Created by olgaoskina
 * 02 May 2015
 */
public class Scheduler {

    private final int threadCount;
    private final WorkerThread[] threads;
    private final List<TaskFuture<UUID>> futures = new ArrayList<>();
    private final Map<UUID, TaskFuture<UUID>> allFutures = new HashMap<>();

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
        taskFuture.setStatus(TaskFuture.Status.WAITING);
        allFutures.put(taskFuture.getId(), taskFuture);
        synchronized (futures) {
            futures.add(taskFuture);
            futures.notify();
        }
        return taskFuture;
    }

    public Optional<TaskFuture<UUID>> getFutureById(UUID id) {
        return Optional.ofNullable(allFutures.get(id));
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
                future.setStatus(TaskFuture.Status.RUNNING);
                try {
                    future.getTask().run();
                    future.setStatus(TaskFuture.Status.COMPLETED);
                } catch (RuntimeException e) {
                    // If we don't catch RuntimeException,
                    // the pool could leak threads
                }
            }
        }
    }
}
