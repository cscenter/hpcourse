import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Created by olgaoskina
 * 03 May 2015
 */
public class TaskFuture<Type> implements Future<Type> {

    private Runnable task;
    private Scheduler.WorkerThread thread;
    private final UUID id = UUID.randomUUID();

    public void setTask(Runnable task) {
        this.task = task;
    }

    public Runnable getTask() {
        return task;
    }

    public Scheduler.WorkerThread getThread() {
        return thread;
    }

    public void setThread(Scheduler.WorkerThread thread) {
        this.thread = thread;
    }

    public UUID getId() {
        return id;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return false;
    }

    @Override
    public boolean isCancelled() {
        return false;
    }

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public Type get() throws InterruptedException, ExecutionException {
        return null;
    }

    @Override
    public Type get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return null;
    }
}
