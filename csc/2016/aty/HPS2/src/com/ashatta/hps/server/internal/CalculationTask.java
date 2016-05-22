package com.ashatta.hps.server.internal;

import com.ashatta.hps.communication.Protocol;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/* Represents a single task. Executed by TaskManager's WorkerThread. Waits for completion of all the task
 * on which it is dependent, does the calculation, calls back to TaskManager after submission and completion
 * for bookkeeping and sending responses to clients.
 */
public class CalculationTask implements Runnable {
    private static AtomicInteger maxId = new AtomicInteger(0);

    private final TaskManager taskManager;
    private final String clientId;
    private final long submitRequestId;
    private final Protocol.Task protoTask;
    private final int taskId;
    private final List<Integer> dependents;
    /* Does not need to be atomic or synchronized: a value is assigned only once and then completed flag is set.
     * Cannot be read before the flag is set (violation of this rule will raise IllegalStateException).
     * Thus, data race on result is impossible.
     */
    private long result;
    /* True if something fails during the execution of the task (e.g. division by zero),
     * used to submit error message to the client.
     */
    private boolean error;
    /* Atomic for visibility. */
    private AtomicBoolean completed;

    public CalculationTask(TaskManager taskManager, String clientId, long submitRequestId, Protocol.Task protoTask) {
        this.taskManager = taskManager;
        this.clientId = clientId;
        this.submitRequestId = submitRequestId;
        this.protoTask = protoTask;
        this.taskId = maxId.getAndIncrement();
        this.error = false;
        this.completed = new AtomicBoolean(false);

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

    public List<Integer> dependents() {
        return this.dependents;
    }

    public int getTaskId() {
        return taskId;
    }

    public long getResult() {
        if (isCompleted() && !hasError()) {
            return result;
        } else {
            throw new IllegalStateException("Getting result of an incomplete or erroneous task");
        }
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

    boolean isCompleted() {
        return completed.get();
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

        /* If a task on which we are dependent has an error we will still send submission response with OK status,
         * but will not be able to calculate the result of this task so it will also get the error flag set.
         * In other words, the submission request will be successful but not subscription requests.
         * I am not sure that such behaviour is reasonable and intended.
         */
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
            completed.set(true);
            synchronized (this) {
                taskManager.taskCompleted(this);
                this.notifyAll();
            }
        }
    }
}
