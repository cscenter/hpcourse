package utils;

import communication.Protocol;

public class TaskDescription {

    private String clientId;
    private boolean isDone = false;
    private Protocol.Task task;
    private Protocol.Status status;
    private Long result;

    public TaskDescription(String clientId, Protocol.Task task) {
        this.clientId = clientId;
        this.task = task;
    }

    public TaskDescription setResult(Long result) {
        this.result = result;
        return this;
    }

    public TaskDescription setStatus(Protocol.Status status) {
        this.status = status;
        return this;
    }

    public TaskDescription setDone() {
        isDone = true;
        return this;
    }

    public boolean isDone() {
        return isDone;
    }

    public Protocol.Task getTask() {
        return task;
    }

    public long getResult() {
        return result;
    }

    public Protocol.Status getStatus() {
        return status;
    }

    public String getClientId() {
        return clientId;
    }

}
