package com.ashatta.hps.server.internal;

import com.ashatta.hps.communication.Protocol;
import com.ashatta.hps.server.Server;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/* TaskManager runs tasks and calls back to the server to send responses to clients.
 * Uses wait-notify mechanism to implement an internal thread pool.
 * Submission and subscription requests are handled asynchronously, task listing request is synchronous.
 */
public class TaskManager {
    /* General worker thread for a thread pool. Extracts tasks from a shared queue and runs them. */
    private class WorkerThread extends Thread {
        private AtomicBoolean interrupted = new AtomicBoolean(false);

        public void kill() {
            interrupted.set(true);
            interrupt();
        }

        public void run() {
            while (!interrupted.get()) {
                Runnable task;
                synchronized (waitingQueue) {
                    while (waitingQueue.isEmpty()) {
                        try {
                            waitingQueue.wait();
                        } catch (InterruptedException e) {
                            if (interrupted.get()) {
                                return;
                            }
                        }
                    }
                    task = waitingQueue.remove();
                }

                task.run();
            }
        }
    }

    /* Server callback to send responses. */
    private final Server server;

    /* Maps task id to a task. */
    private final Map<Integer, CalculationTask> taskById = new HashMap<>();

    /* Assigned and filled once in the constructor. Used to shutdown the server and stop all threads, */
    private final List<WorkerThread> threads;

    /* Submission and subscription requests are put on this queue by TaskManager and extracted by WorkerThreads. */
    private final Queue<Runnable> waitingQueue = new ArrayDeque<>();

    /* Set of tasks that completed execution successfully or with an error. */
    private final Set<Integer> complete = new HashSet<>();
    /* Set of tasks that have already started execution. */
    private final Set<Integer> running = new HashSet<>();

    public TaskManager(Server server, int threadsNumber) {
        this.server = server;
        threads = new ArrayList<>(threadsNumber);
        for (int i = 0; i < threadsNumber; ++i) {
            WorkerThread thread = new WorkerThread();
            threads.add(thread);
            thread.start();
        }
    }

    public void shutdown() {
        for (WorkerThread thread : threads) {
            thread.kill();
        }
    }

    public void submit(String clientId, long submitRequestId, Protocol.Task protoTask) {
        if (!verifyTaskSubmission(protoTask)) {
            server.sendSubmitResponse(submitRequestId, 0, false);
            return;
        }

        CalculationTask task = new CalculationTask(this, clientId, submitRequestId, protoTask);
        synchronized (taskById) {
            taskById.put(task.getTaskId(), task);
        }
        synchronized (waitingQueue) {
            waitingQueue.add(task);
            waitingQueue.notify();
        }
    }

    private boolean verifyTaskSubmission(Protocol.Task protoTask) {
        return protoTask.hasA() && verifyTaskParam(protoTask.getA()) &&
                protoTask.hasB() && verifyTaskParam(protoTask.getB()) &&
                protoTask.hasM() && verifyTaskParam(protoTask.getM()) &&
                protoTask.hasP() && verifyTaskParam(protoTask.getP()) &&
                protoTask.hasN();
    }

    private boolean verifyTaskParam(Protocol.Task.Param param) {
        return param.hasValue() || (param.hasDependentTaskId() && getTask(param.getDependentTaskId()) != null);
    }

    public void subscribe(long requestId, int taskId) {
        CalculationTask task = getTask(taskId);
        if (task == null) {
            server.sendSubscribeResponse(requestId, 0, false);
        }

        SubscriptionTask subscriptionTask = new SubscriptionTask(this, task, requestId);
        synchronized (waitingQueue) {
            waitingQueue.add(subscriptionTask);
            waitingQueue.notify();
        }
    }

    public void listAll(long requestId) {
        /* Only sending a list of running and completed tasks, but not those which are waiting for start.
         * Current implementation ensures that the list of tasks is consistent, i.e. the returned state
         * was an actual state of the server at the time of locking. For this purpose both lists are locked
         * simultaneously and sent to the server separately. */
        List<CalculationTask> runningTasks = new ArrayList<>();
        List<CalculationTask> completeTasks = new ArrayList<>();
        synchronized (running) {
            synchronized (complete) {
                for (int taskId : running) {
                    synchronized (taskById) {
                        runningTasks.add(taskById.get(taskId));
                    }
                }
                for (int taskId : complete) {
                    synchronized (taskById) {
                        completeTasks.add(taskById.get(taskId));
                    }
                }
            }
        }
        server.sendListTasksResponse(requestId, runningTasks, completeTasks, true);
    }

    void taskSubmitted(CalculationTask task, long requestId) {
        synchronized (running) {
            running.add(task.getTaskId());
        }
        server.sendSubmitResponse(requestId, task.getTaskId(), true);
    }

    void taskCompleted(CalculationTask task) {
        int taskId = task.getTaskId();
        synchronized (running) {
            synchronized (complete) {
                running.remove(taskId);
                complete.add(taskId);
            }
        }
    }

    /* This will send a message with status ERROR if the execution of a task fails (for example, because of division
         by zero), it will not be sent in the submit response for that task.
     */
    void subscriptionNotify(CalculationTask task, long requestId) {
        server.sendSubscribeResponse(requestId, task.hasError() ? 0 : task.getResult(), !task.hasError());
    }

    CalculationTask getTask(int taskId) {
        CalculationTask task;
        synchronized (taskById) {
            task = taskById.get(taskId);
        }
        return task;
    }

    long getTaskResult(int taskId) {
        return getTask(taskId).getResult();
    }
}
