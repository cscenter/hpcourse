package com.ashatta.hps.server.internal;

import com.ashatta.hps.communication.Protocol;
import com.ashatta.hps.server.Server;

import java.util.*;

/* TaskManager runs tasks and calls back to the server to send responses to clients.
 * Uses wait-notify mechanism to implement an internal thread pool.
 * Submission and subscription requests are handled asynchronously, task listing request is synchronous.
 */
public class TaskManager {
    private class WorkerThread extends Thread {
        public void run() {
            while (true) {
                Runnable task;
                synchronized (waitingQueue) {
                    while (waitingQueue.isEmpty()) {
                        try {
                            waitingQueue.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
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

    private final Queue<Runnable> waitingQueue = new ArrayDeque<>();

    private final Set<Integer> complete = new HashSet<>();
    /* Set of tasks that have already started execution. */
    private final Set<Integer> running = new HashSet<>();

    public TaskManager(Server server, int threadsNumber) {
        this.server = server;
        for (int i = 0; i < threadsNumber; ++i) {
            new WorkerThread().start();
        }
    }

    public void submit(String clientId, long submitRequestId, Protocol.Task protoTask) {
        CalculationTask task = new CalculationTask(this, clientId, submitRequestId, protoTask);
        synchronized (taskById) {
            taskById.put(task.getTaskId(), task);
        }
        synchronized (waitingQueue) {
            waitingQueue.add(task);
            waitingQueue.notify();
        }
    }

    public void subscribe(long requestId, int taskId) {
        CalculationTask task;
        synchronized (taskById) {
            task = taskById.get(taskId);
        }
        SubscriptionTask subscriptionTask = new SubscriptionTask(this, task, requestId);
        synchronized (waitingQueue) {
            waitingQueue.add(subscriptionTask);
            waitingQueue.notify();
        }
    }

    /* This will send a message with status ERROR if the execution of a task fails (for example, because of division
         by zero), it will not be sent in the submit response for that task.
     */
    public void subscriptionNotify(CalculationTask task, long requestId) {
        server.sendSubscribeResponse(requestId, task.getResult(), !task.hasError());
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

    public void taskSubmitted(CalculationTask task, long requestId) {
        synchronized (running) {
            running.add(task.getTaskId());
        }
        server.sendSubmitResponse(requestId, task.getTaskId(), true);
    }

    public void taskCompleted(CalculationTask task) {
        int taskId = task.getTaskId();
        synchronized (running) {
            synchronized (complete) {
                running.remove(taskId);
                complete.add(taskId);
            }
        }
    }

    public CalculationTask getTask(int taskId) {
        CalculationTask task;
        synchronized (taskById) {
            task = taskById.get(taskId);
        }
        return task;
    }

    public long getTaskResult(int taskId) {
        return getTask(taskId).getResult();
    }
}
