import java.util.*;
import java.util.concurrent.Callable;

/**
 * Created by olgaoskina
 * 02 May 2015
 */
public class Scheduler {

    private final WorkerThread[] threads;
    private final List<TaskFuture> futures = new ArrayList<>();
    private final Map<Long, TaskFuture> allFutures = new HashMap<>();

    public Scheduler(int threadCount) {
        threads = new WorkerThread[threadCount];

        for (WorkerThread thread : threads) {
            thread = new WorkerThread();
            thread.start();
        }
    }

    public TaskFuture submit(Callable task) {
        TaskFuture taskFuture = new TaskFuture();
        taskFuture.setTask(task);
        taskFuture.setStatus(TaskFuture.Status.WAITING);
        allFutures.put(taskFuture.getId(), taskFuture);
        synchronized (futures) {
            futures.add(taskFuture);
            futures.notify();
        }
        return taskFuture;
    }

    public Optional<TaskFuture> getFutureById(long id) {
        return Optional.ofNullable(allFutures.get(id));
    }

    public Optional<TaskFuture.Status> getStatus(long id) {
        Optional<TaskFuture> future = getFutureById(id);
        if (future.isPresent()) {
            return Optional.of(future.get().getStatus());
        } else {
            return Optional.empty();
        }
    }

    public boolean interrupt(long id) {
        Optional<TaskFuture> mayBeFuture = getFutureById(id);
        if (mayBeFuture.isPresent()) {
            //            TODO: COMPLETED?
            TaskFuture future = mayBeFuture.get();
            future.interrupt();
            return true;
        } else {
            return false;
        }
    }

    public void exit() {
        synchronized (futures) {
            futures.clear();
        }
        for (WorkerThread thread : threads) {
            thread.setKill(true);
            thread.interrupt();
        }
    }

    public Optional<Long> getResult(long id) {
        TaskFuture future = allFutures.get(id);
        if (future != null) {
            return Optional.of(future.get());
        } else {
            return Optional.empty();
        }
    }

    public class WorkerThread extends Thread {
        private boolean isKill = false;

        @Override
        public void run() {
            while (!isKill) {
                TaskFuture future;
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
                    try {
                        future.getTask().call();
                        future.setStatus(TaskFuture.Status.COMPLETED);
                    } catch (Exception e) {
                        future.setStatus(TaskFuture.Status.INTERRUPTED);
                    }
                } catch (RuntimeException e) {
                    // If we don't catch RuntimeException,
                    // the pool could leak threads
                }
            }
        }

        public void setKill(boolean isKill) {
            this.isKill = isKill;
        }
    }
}
