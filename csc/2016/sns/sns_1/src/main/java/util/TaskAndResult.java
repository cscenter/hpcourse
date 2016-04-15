package util;

import communication.Protocol;

import java.util.logging.Logger;

/**
 * Created by nikita.sokeran@gmail.com
 */
public class TaskAndResult {

    private static final Logger LOGGER = Logger.getLogger(TaskAndResult.class.getName());

    private final Protocol.Task task;
    private final String clientId;

    private final Object lock = new Object();

    private volatile ValueWrapper<Long> result = new ValueWrapper<>(Long.class);
    private volatile ValueWrapper<Protocol.Status> status = new ValueWrapper<>(Protocol.Status.class);

    public TaskAndResult(final Protocol.Task task, final String clientId) {
        this.task = task;
        this.clientId = clientId;
    }

    public String getClientId() {
        return clientId;
    }

    public boolean hasStatus() {
        return status.hasValue();
    }

    /**
     * Blocking operation
     *
     * @return result value
     */
    public Long getResult() {
        return result.getValue();
    }

    /**
     * Blocking operation
     *
     * @return status value
     */
    public Protocol.Status getStatus() {
        return status.getValue();
    }

    public void setResult(final Protocol.Status status, final Long result) {
        //Use double-check to reduce time in synchronized block when value is set
        if (!this.result.hasValue()) {
            synchronized (lock) {
                if (!this.result.hasValue()) {
                    try {
                        this.result.setValue(result);
                        this.status.setValue(status);
                    } catch (CheckedClassCastException e) {
                        //Must not ever occur
                        LOGGER.warning("Exception while casting result and status values: " + e);
                    }
                    lock.notifyAll();
                }
            }
        }
    }

    public Protocol.Task getTask() {
        return task;
    }
}
