package util;

import communication.Protocol;

/**
 * Created by nikita.sokeran@gmail.com
 */
public class TaskAndResult {
    private final Protocol.Task task;

    private final Object lock = new Object();

    private Long result;

    public TaskAndResult(final Protocol.Task task) {
        this.task = task;
    }

    public boolean hasResult() {
        return result != null;
    }

    public Long getResult() {
        synchronized (lock) {
            while (result == null) {
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            return result;
        }
    }

    public void setResult(final Long result) {
        if (this.result == null) {
            synchronized (lock) {
                lock.notifyAll();
                this.result = result;
            }
        }
    }

    public Protocol.Task getTask() {
        return task;
    }
}
