import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;

/**
 * Created by olgaoskina
 * 03 May 2015
 */
public class TaskFuture {

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

    private Callable task;
    private Scheduler.WorkerThread thread;
    private final long id = UniqueID.createID();
    private volatile Status status;
    private Exception internalException = null;

    public void setTask(Callable task) {
        this.task = task;
    }

    public Callable getTask() {
        return task;
    }

    public void setThread(Scheduler.WorkerThread thread) {
        this.thread = thread;
    }

    public long getId() {
        return id;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Exception getInternalException() {
        return internalException;
    }

    public void setInternalException(Exception internalException) {
        this.internalException = internalException;
    }

    public void interrupt() {
        thread.interrupt();
        status = Status.INTERRUPTED;
    }

    public long get() {
        while (status != Status.COMPLETED) {
            if (status == Status.INTERRUPTED) {
                throw new CancellationException();
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return id;
    }
}
