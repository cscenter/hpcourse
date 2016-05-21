package server.TaskHelper;

import communication.Protocol;

public class Task {

    private long taskId;
    private Protocol.Task protoTask;
    private String clientId;
    private volatile boolean isReady;
    private volatile Long result;

    public Task(Protocol.Task protoTask, long  id, String clientId) {
        this.taskId = id;
        this.clientId = clientId;
        this.protoTask = protoTask;
        isReady = false;
        result = null;
    }

    boolean isReady() {
        return isReady;
    }
    public void setReady(boolean val) {
        isReady = val;
    }

    public Protocol.Task getProtoTask() {
        return protoTask;
    }

    public String getClientId() {
        return clientId;
    }

    public Long getResult() {
        return result;
    }

    public void setResult(Long result) {
        this.result = result;
    }

    public long getTaskId() {
        return taskId;
    }
}
