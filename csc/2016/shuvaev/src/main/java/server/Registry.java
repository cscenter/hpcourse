package server;

import communication.Protocol;

import java.util.*;
import java.util.stream.Collectors;

public class Registry {
    private HashMap<Integer, Optional<Long>> values = new HashMap<>();
    private HashMap<Integer, Protocol.ListTasksResponse.TaskDescription> tasks = new HashMap<>();

    public synchronized Optional<Long> getValue(int taskId) {
        return values.get(taskId);
    }

    public synchronized void addValue(int taskId, Optional<Long> value) {
        values.put(taskId, value);
    }

    public synchronized void addTask(int taskId, String clientId, Protocol.Task task) {
        tasks.put(taskId, Protocol.ListTasksResponse.TaskDescription.newBuilder()
                .setClientId(clientId)
                .setTaskId(taskId)
                .setTask(task).build());
    }

    public synchronized Collection<Protocol.ListTasksResponse.TaskDescription> getAllTasks() {
        List<Protocol.ListTasksResponse.TaskDescription> descriptions = tasks.values().stream().collect(Collectors.toList());
        return  descriptions;
    }

}
