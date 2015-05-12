package hw;

import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

// a la http://docs.oracle.com/javase/7/docs/api/java/util/concurrent/Future.html
public class Future<V> implements Runnable {
    private final Callable task;
    private final AtomicInteger status = new AtomicInteger(TaskStatus.NOTSTARTED.getValue());
    private Exception exception;
    private V result;
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

    public V getResult() {
        return result;
    }

    public boolean cancel() {
        TaskStatus s = getStatus();

        if (s == TaskStatus.DONE) {
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
        if (getStatus() == TaskStatus.CANCELLED) {
            thread.interrupt();
            notifyTask();
        }
        return false;
    }

    public V get() throws InterruptedException, ExecutionException {
        while (!(getStatus() == TaskStatus.DONE || getStatus() == TaskStatus.CANCELLED)) {
            synchronized (task) {
                task.wait();
            }
        }

        if ((exception instanceof InterruptedException) || getStatus() == TaskStatus.CANCELLED) {
            throw new CancellationException();
        }

        if (exception != null) {
            throw new ExecutionException(exception);
        }

        return result;
    }

    @Override
    public void run() {
        if (!casStatus(TaskStatus.NOTSTARTED, TaskStatus.RUNNING)) {
            return;
        }
        try {
            thread = Thread.currentThread();
            result = (V) task.call();
        } catch (Exception ex) {
            exception = ex;
        } finally {
            casStatus(TaskStatus.RUNNING, TaskStatus.DONE);
            notifyTask();
        }
    }

    private boolean casStatus(TaskStatus from, TaskStatus to) {
        return status.compareAndSet(from.getValue(), to.getValue());
    }

    private void notifyTask() {
        synchronized (task) {
            task.notify();
        }
    }
}


