package com.ashatta.hps.server.internal;

import com.ashatta.hps.server.Server;

import java.io.IOException;
import java.util.*;

public class TaskManager {
    private final Server server;

    private final Map<Integer, Long> submitRequest = new HashMap<>();
    private final Map<Integer, Set<Long>> subscriptionRequests = new HashMap<>();

    private final Map<Integer, Task> taskById = new HashMap<>();

    private final Set<Integer> complete = new HashSet<>();
    private final Set<Integer> running = new HashSet<>();
    /* Maps task's id to ids of other tasks which depend on this task's result. */
    private final Map<Integer, Set<Integer>> waiting = new HashMap<>();

    public TaskManager(Server server) {
        this.server = server;
    }

    public void submit(long requestId, Task task) throws IOException {
        taskById.put(task.getTaskId(), task);
        submitRequest.put(task.getTaskId(), requestId);

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
    }

    public void subscribe(long requestId, int taskId) throws IOException{
        if (!subscriptionRequests.containsKey(taskId)) {
            subscriptionRequests.put(taskId, new HashSet<Long>());
        }
        subscriptionRequests.get(taskId).add(requestId);

        if (complete.contains(taskId)) {
            subscriptionNotify(taskId);
        }
    }

    private void subscriptionNotify(int taskId) throws IOException {
        for (long requestId : subscriptionRequests.get(taskId)) {
            server.sendSubscribeResponse(requestId, taskById.get(taskId).getResult(), true);
        }
        subscriptionRequests.get(taskId).clear();
    }

    public void listAll(long requestId) throws IOException{
        List<Task> result = new ArrayList<>();
        for (int taskId : running) {
            result.add(taskById.get(taskId));
        }
        for (int taskId : complete) {
            result.add(taskById.get(taskId));
        }

        server.sendListAllResponse(requestId, result, true);
    }

    public void taskCompleted(Task task) throws IOException {
        int taskId = task.getTaskId();
        running.remove(taskId);
        complete.add(taskId);
        subscriptionNotify(taskId);

        Set<Integer> dependents = waiting.get(taskId);
        for (int w : waiting.keySet()) {
            waiting.get(w).removeAll(dependents);
        }
        waiting.remove(taskId);

        for (int dependent : dependents) {
            submit(submitRequest.get(dependent), taskById.get(dependent));
        }
    }

    public long getTaskResult(int taskId) {
        return taskById.get(taskId).getResult();
    }
}
