package com.ashatta.hps.server.internal;

import com.ashatta.hps.communication.Protocol;
import com.ashatta.hps.server.Server;

import java.util.*;

/* TaskManager class stores information about Tasks, runs and handles them. All the synchronization happens here. */
public class TaskManager {
    /* Server callback to send responses. */
    private final Server server;

    /* Maps task id to a task. */
    private final Map<Integer, Task> taskById = new HashMap<>();

    private final Set<Integer> complete = new HashSet<>();
    /* Set of tasks that have already started execution. */
    private final Set<Integer> running = new HashSet<>();

    public TaskManager(Server server) {
        this.server = server;
    }

    public void submit(long requestId, Task task) {
        try {
            synchronized (taskById) {
                taskById.put(task.getTaskId(), task);
            }
            CalculationThread thread = new CalculationThread(this, task, requestId);
            thread.start();
        } catch (Exception e) {
            server.sendSubmitResponse(requestId, 0, false);
        }
    }

    public void subscribe(long requestId, int taskId) {
        Task task;
        synchronized (taskById) {
            task = taskById.get(taskId);
        }
        SubscriptionThread thread = new SubscriptionThread(this, task, requestId);
        thread.start();
    }

    /* This will send a message with status ERROR if the execution of a task fails (for example, because of division
         by zero), it will not be sent in the submit response for that task.
     */
    public void subscriptionNotify(Task task, long requestId) {
        server.sendSubscribeResponse(requestId, task.getResult(), !task.hasError());
    }

    public void listAll(long requestId) {
        /* Only sending a list of running and completed tasks, but not those which are waiting for start.
         * Current implementation ensures that the list of tasks is consistent, i.e. the returned state
         * was an actual state of the server at the time of locking. For this purpose both lists are locked
         * simultaneously and sent to the server separately. */
        List<Task> runningTasks = new ArrayList<>();
        List<Task> completeTasks = new ArrayList<>();
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

    public void taskSubmitted(Task task, long requestId) {
        synchronized (running) {
            running.add(task.getTaskId());
        }
        server.sendSubmitResponse(requestId, task.getTaskId(), true);
    }

    public void taskCompleted(Task task) {
        int taskId = task.getTaskId();
        synchronized (running) {
            synchronized (complete) {
                running.remove(taskId);
                complete.add(taskId);
            }
        }
    }

    public Task getTask(int taskId) {
        Task task;
        synchronized (taskById) {
            task = taskById.get(taskId);
        }
        return task;
    }

    public long getTaskResult(int taskId) {
        return getTask(taskId).getResult();
    }
}
