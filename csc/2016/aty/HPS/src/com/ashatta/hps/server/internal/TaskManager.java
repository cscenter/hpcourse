package com.ashatta.hps.server.internal;

import com.ashatta.hps.server.Server;

import java.io.IOException;
import java.util.*;

public class TaskManager {
    private final Server server;
    private final Lock synchronizationLock;

    private final Map<Integer, Long> submitRequest = new HashMap<>();
    private final Map<Integer, Set<Long>> subscriptionRequests = new HashMap<>();

    private final Map<Integer, Task> taskById = new HashMap<>();

    private final Set<Integer> complete = new HashSet<>();
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

    private void subscriptionNotify(int taskId) {
        Task task = taskById.get(taskId);
        for (long requestId : subscriptionRequests.get(taskId)) {
            server.sendSubscribeResponse(requestId, task.getResult(), !task.hasError());
        }
        subscriptionRequests.get(taskId).clear();
    }

    public void listAll(long requestId) {
        synchronizationLock.lock();

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

        Set<Integer> dependents = new HashSet<>();
        if (waiting.containsKey(taskId)) {
            dependents.addAll(waiting.get(taskId));
        }
        for (int w : waiting.keySet()) {
            waiting.get(w).removeAll(dependents);
        }
        waiting.remove(taskId);

        Map<Long, Task> dependentsMap = new HashMap<>();
        for (int dependent : dependents) {
            dependentsMap.put(submitRequest.get(dependent), taskById.get(dependent));
        }

        synchronizationLock.unlock();

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
