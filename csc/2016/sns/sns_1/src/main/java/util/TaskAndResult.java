package util;

import communication.Protocol;

/**
 * Created by nikita.sokeran@gmail.com
 */
public class TaskAndResult {
    private final Protocol.Task task;
    private final String clientId;

    private final Object lock = new Object();

    private volatile Long result;
    private volatile Protocol.Status status;

    public TaskAndResult(final Protocol.Task task, final String clientId) {
        this.task = task;
        this.clientId = clientId;
    }

    public String getClientId() {
        return clientId;
    }

    public boolean hasStatus() {
        return status != null;
    }

    /**
     * Block current thread and wait
     *
     * @return result value
     */
    public Long getResult() {
        if (status == null) {
            synchronized (lock) {
                while (status == null) {
                    try {
                        lock.wait();
                    } catch (InterruptedException e) {
                        return null;
                    }
                }
            }
        }
        return result;
    }

    public Protocol.Status getStatus() {
        if (status == null) {
            synchronized (lock) {
                while (status == null) {
                    try {
                        lock.wait();
                    } catch (InterruptedException e) {
                        return null;
                    }
                }
            }
        }
        return status;
    }

    public void setResult(final Protocol.Status status, final Long result) {
        //Use double-check to reduce time in synchronized block when value is set
        if (this.result == null) {
            synchronized (lock) {
                if (this.result == null) {
                    this.result = result;
                    this.status = status;
                    lock.notifyAll();
                }
            }
        }
    }

    public Protocol.Task getTask() {
        return task;
    }
}
