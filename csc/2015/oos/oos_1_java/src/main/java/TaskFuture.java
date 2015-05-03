import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * Created by olgaoskina
 * 03 May 2015
 */
public class TaskFuture<Type> {

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
    private Status status;

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

    public void interrupt() {
        thread.interrupt();
        status = Status.INTERRUPTED;
    }

    public Type get() throws Exception {
        return null;
    }

    public Type get(long timeout, TimeUnit unit) throws Exception {
        return null;
    }
}
