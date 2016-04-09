package server;

import communication.Protocol;

/**
 * Created by dkorolev on 4/5/2016.
 */
public class TaskDescFull {
    public final String clientId;
    public final Protocol.Task task;
    public Long result;
    public boolean hasError;

    public TaskDescFull(String clientId, Protocol.Task task) {
        this.clientId = clientId;
        this.task = task;
    }
}
