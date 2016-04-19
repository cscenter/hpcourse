package com.ashatta.hps.server.internal;

import com.ashatta.hps.server.Server;

import java.util.*;

/* TaskManager class stores information about Tasks, runs and handles them. All the synchronization happens here. */
public class TaskManager {
    /* Server callback to send responses. */
    private final Server server;
    /* Only locks TaskManager operations so that all the tasks can run in parallel and not be affected
        by synchronization. All synchronization that happens affects only tasks' submission/completion.
     */
    private final Lock synchronizationLock;

    /* Maps task ids to ids of submission requests */
    private final Map<Integer, Long> submitRequest = new HashMap<>();
    /* Maps task ids to ids of subscription requests (a set because multiple clients might subscribe to the same task). */
    private final Map<Integer, Set<Long>> subscriptionRequests = new HashMap<>();

    /* Maps task id to a task. */
    private final Map<Integer, Task> taskById = new HashMap<>();

    private final Set<Integer> complete = new HashSet<>();
    /* Set of tasks that have already started execution. */
    private final Set<Integer> running = new HashSet<>();
    /* Maps task's id to ids of other tasks which depend on this task's result. */
    private final Map<Integer, Set<Integer>> waiting = new HashMap<>();

    public TaskManager(Server server) {
        this.server = server;
        this.synchronizationLock = new Lock();
    }

    public void submit(long requestId, Task task) {
        synchronizationLock.lock();

        try {
            taskById.put(task.getTaskId(), task);
            submitRequest.put(task.getTaskId(), requestId);
            subscriptionRequests.put(task.getTaskId(), new HashSet<Long>());

            boolean delay = false;
            for (int dependentId : task.dependents()) {
                if (!complete.contains(dependentId)) {
                    if (!waiting.containsKey(dependentId)) {
                        waiting.put(dependentId, new HashSet<Integer>());
                    }
                    waiting.get(dependentId).add(task.getTaskId());
                    delay = true;
                }
            }

            if (!delay) {
                running.add(task.getTaskId());
                task.start();
                server.sendSubmitResponse(requestId, task.getTaskId(), true);
                submitRequest.remove(task.getTaskId());
            }
        } catch (Exception e) {
            server.sendSubmitResponse(requestId, 0, false);
        } finally {
            synchronizationLock.unlock();
        }
    }

    public void subscribe(long requestId, int taskId) {
        synchronizationLock.lock();

        subscriptionRequests.get(taskId).add(requestId);

        if (complete.contains(taskId)) {
            subscriptionNotify(taskId);
        }

        synchronizationLock.unlock();
    }

    /* This will send a message with status ERROR if the execution of a task fails, it will not be sent in
        the submit response for that task.
     */
    private void subscriptionNotify(int taskId) {
        Task task = taskById.get(taskId);
        for (long requestId : subscriptionRequests.get(taskId)) {
            server.sendSubscribeResponse(requestId, task.getResult(), !task.hasError());
        }
        subscriptionRequests.get(taskId).clear();
    }

    public void listAll(long requestId) {
        synchronizationLock.lock();

        /* Only sending list of running and completed tasks, but not those who are waiting for start. */
        List<Task> result = new ArrayList<>();
        for (int taskId : running) {
            result.add(taskById.get(taskId));
        }
        for (int taskId : complete) {
            result.add(taskById.get(taskId));
        }

        synchronizationLock.unlock();

        server.sendListTasksResponse(requestId, result, true);
    }

    public void taskCompleted(Task task) {
        synchronizationLock.lock();

        int taskId = task.getTaskId();
        running.remove(taskId);
        complete.add(taskId);
        subscriptionNotify(taskId);

        /* Getting all tasks that are dependent on the one that has just completed to try to restart them later. */
        Set<Integer> dependents = new HashSet<>();
        if (waiting.containsKey(taskId)) {
            dependents.addAll(waiting.get(taskId));
        }
        for (int w : waiting.keySet()) {
            /* Removing them here because they'll be added again in the submit method. */
            waiting.get(w).removeAll(dependents);
        }
        waiting.remove(taskId);

        Map<Long, Task> dependentsMap = new HashMap<>();
        for (int dependent : dependents) {
            dependentsMap.put(submitRequest.get(dependent), taskById.get(dependent));
        }

        synchronizationLock.unlock();

        /* 'Resubmission' of dependent tasks. They will run now if they can, otherwise they will
            get back on waiting lists of other tasks who haven't completed yet.
         */
        for (Map.Entry<Long, Task> entry : dependentsMap.entrySet()) {
            submit(entry.getKey(), entry.getValue());
        }
    }

    public long getTaskResult(int taskId) {
        synchronizationLock.lock();
        Task task = taskById.get(taskId);
        synchronizationLock.unlock();
        return task.getResult();
    }
}
