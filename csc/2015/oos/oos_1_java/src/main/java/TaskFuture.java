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

    public enum Status {
        WAITING("WAITING"), RUNNING("RUNNING"), INTERRUPTED("INTERRUPTED"), COMPLETED("COMPLETED");

        private final String nameStatus;

        private Status(String nameStatus) {
            this.nameStatus = nameStatus;
        }

        @Override
        public String toString() {
            return nameStatus;
        }
    }

    private Runnable task;
    private Scheduler.WorkerThread thread;
    private final UUID id = UUID.randomUUID();
    private Status status;

    public void setTask(Runnable task) {
        this.task = task;
    }

    public Runnable getTask() {
        return task;
    }

    public void setThread(Scheduler.WorkerThread thread) {
        this.thread = thread;
    }

    public UUID getId() {
        return id;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public void interrupt() {
        thread.interrupt();
        status = Status.INTERRUPTED;
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
