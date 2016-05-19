package com.ashatta.hps.server.internal;

import com.ashatta.hps.communication.Protocol;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/* A thread representing a single task. Runs the task and then calls back to the TaskManager
    so that it can submit subscription responses and keep its data structures up-to-date.
*/
public class Task {
    private static AtomicInteger maxId = new AtomicInteger(0);

    private final TaskManager manager;
    private final String clientId;
    private final Protocol.Task protoTask;
    private final int taskId;
    private final List<Integer> dependents;
    private long result;
    /* True if something fails during the execution of the task (e.g. division by zero),
        used to submit error message to the client.
     */
    private boolean error;
    private boolean completed;

    public Task(String clientId, Protocol.Task protoTask, TaskManager taskManager) {
        this.manager = taskManager;
        this.clientId = clientId;
        this.protoTask = protoTask;
        this.taskId = maxId.getAndIncrement();
        this.error = false;
        this.completed = false;

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

    public boolean hasError() {
        return error;
    }

    public boolean isCompleted() {
        return completed;
    }

    private long getValue(Protocol.Task.Param param) {
        return param.hasValue()
                ? param.getValue()
                : manager.getTaskResult(param.getDependentTaskId());
    }

    public void run() {
        try {
            long a = getValue(protoTask.getA());
            long b = getValue(protoTask.getB());
            long p = getValue(protoTask.getP());
            long m = getValue(protoTask.getM());

            long n = protoTask.getN();

            while (n-- > 0)
            {
                b = (a * p + b) % m;
                a = b;
            }

            result = a;
        } catch (Exception e) {
            error = true;
        } finally {
            completed = true;
        }
    }
}
