package hw;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

// a la http://docs.oracle.com/javase/7/docs/api/java/util/concurrent/Future.html
public class Future implements Runnable {
    private final Callable task;
    private final AtomicInteger status = new AtomicInteger(TaskStatus.NOTSTARTED.getValue());
    private Exception exception;
    private Object result;
    private Thread thread;

    public Future(Callable task) {
        this.task = task;
    }

    public TaskStatus getStatus() {
        return TaskStatus.get(status.get());
    }

    public Exception getException() {
        return exception;
    }

    public Object getResult() {
        return result;
    }

    // Attempts to cancel execution of this task.
    public boolean cancel() {
        TaskStatus s = getStatus();

        if(s == TaskStatus.DONE){
            return true;
        }

        if (s == TaskStatus.CANCELLED) {
            return false;
        }

        if (casStatus(TaskStatus.NOTSTARTED, TaskStatus.CANCELLED)) {
            return false;
        }
        casStatus(TaskStatus.RUNNING, TaskStatus.CANCELLED);
        casStatus(TaskStatus.NOTSTARTED, TaskStatus.CANCELLED);
        thread.interrupt();
        return false;
    }


    @Override
    public void run() {
        if (!casStatus(TaskStatus.NOTSTARTED, TaskStatus.RUNNING)) {
            return;
        }
        try {
            thread = Thread.currentThread();
            result = task.call();
        } catch (Exception ex) {
            exception = ex;
        } finally {
            casStatus(TaskStatus.RUNNING, TaskStatus.DONE);
        }
    }

    private boolean casStatus(TaskStatus from, TaskStatus to) {
        return status.compareAndSet(from.getValue(), to.getValue());
    }
}


