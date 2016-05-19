package com.ashatta.hps.server.internal;

import com.ashatta.hps.communication.Protocol;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/* A thread representing a single task. Runs the task and then calls back to the TaskManager
    so that it can submit subscription responses and keep its data structures up-to-date.
*/
public class CalculationTask implements Runnable {
    private static AtomicInteger maxId = new AtomicInteger(0);

    private final TaskManager taskManager;
    private final String clientId;
    private final long submitRequestId;
    private final Protocol.Task protoTask;
    private final int taskId;
    private final List<Integer> dependents;
    private long result;
    /* True if something fails during the execution of the task (e.g. division by zero),
        used to submit error message to the client.
     */
    private boolean error;
    private boolean completed;

    public CalculationTask(TaskManager taskManager, String clientId, long submitRequestId, Protocol.Task protoTask) {
        this.taskManager = taskManager;
        this.clientId = clientId;
        this.submitRequestId = submitRequestId;
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
                : taskManager.getTaskResult(param.getDependentTaskId());
    }

    public void run() {
        for (int dependentId : dependents()) {
            CalculationTask dependent = taskManager.getTask(dependentId);
            synchronized (dependent) {
                if (!dependent.isCompleted()) {
                    try {
                        dependent.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        taskManager.taskSubmitted(this, submitRequestId);

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
            synchronized (this) {
                taskManager.taskCompleted(this);
                this.notifyAll();
            }
        }
    }
}