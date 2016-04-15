package com.ashatta.hps.server.internal;

import com.ashatta.hps.communication.Protocol;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class Task extends Thread {
    private static AtomicInteger maxId = new AtomicInteger(0);

    private final TaskManager manager;
    private final String clientId;
    private final Protocol.Task protoTask;
    private final int taskId;
    private final List<Integer> dependents;
    private long result;

    public Task(String clientId, Protocol.Task protoTask, TaskManager taskManager) {
        this.manager = taskManager;
        this.clientId = clientId;
        this.protoTask = protoTask;
        this.taskId = maxId.getAndIncrement();

        dependents = new ArrayList<>();
        if (protoTask.getA().hasDependentTaskId()) {
            dependents.add(protoTask.getA().getDependentTaskId());
        }
        if (protoTask.getB().hasDependentTaskId()) {
            dependents.add(protoTask.getB().getDependentTaskId());
        }
        if (protoTask.getP().hasDependentTaskId()) {
            dependents.add(protoTask.getP().getDependentTaskId());
        }
        if (protoTask.getM().hasDependentTaskId()) {
            dependents.add(protoTask.getM().getDependentTaskId());
        }
    }

    List<Integer> dependents() {
        return this.dependents;
    }

    public int getTaskId() {
        return taskId;
    }

    public long getResult() {
        return result;
    }

    public String getClientId() {
        return clientId;
    }

    public Protocol.Task getProtoTask() {
        return protoTask;
    }

    public void run() {
        long a = (protoTask.getA().hasValue()
                ? protoTask.getA().getValue()
                : manager.getTaskResult(protoTask.getA().getDependentTaskId()));
        long b = (protoTask.getB().hasValue()
                ? protoTask.getB().getValue()
                : manager.getTaskResult(protoTask.getB().getDependentTaskId()));
        long p = (protoTask.getP().hasValue()
                ? protoTask.getP().getValue()
                : manager.getTaskResult(protoTask.getP().getDependentTaskId()));
        long m = (protoTask.getM().hasValue()
                ? protoTask.getM().getValue()
                : manager.getTaskResult(protoTask.getM().getDependentTaskId()));

        long n = protoTask.getN();

        while (n-- > 0)
        {
            b = (a * p + b) % m;
            a = b;
        }

        result = a;
        try {
            manager.taskCompleted(this);
        }
        catch (IOException e) {}
    }
}
