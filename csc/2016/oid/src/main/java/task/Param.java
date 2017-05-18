package task;

import protocol.Protocol;

public class Param {
    private long value;
    private Task parentTask;

    private volatile boolean ready;

    private Param(long value) {
        this.value = value;
    }

    private Param(Task parentTask) {
        this.parentTask = parentTask;
    }


    public static Param newParamWithValue(long value) {
        return new Param(value);
    }

    public static Param newParamWithParentTask(Task parentTask) {
        return new Param(parentTask);
    }

    public long getValue() {
        return value;
    }

    public Task getParentTask() {
        return parentTask;
    }

    public boolean isReady() {
        return ready;
    }

    public Protocol.Task.Param toProtocolParam() {
        Protocol.Task.Param.Builder param = Protocol.Task.Param.newBuilder();
        param.setValue(value);
        return param.build();
    }
}
