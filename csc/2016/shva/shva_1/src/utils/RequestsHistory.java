package utils;

import communication.Protocol;

import java.util.*;

public class RequestsHistory {

    private static Map<Integer, TaskDescription> history
            = Collections.synchronizedMap(new HashMap<>());

    private RequestsHistory() {}

    public static synchronized void putTask(int taskId, String clientId, Protocol.Task task) {
        history.put(taskId, new TaskDescription(clientId, task));
    }

    public static synchronized TaskDescription getTaskDescriptionById(int id) {
        return history.get(id);
    }

    public static List<Protocol.ListTasksResponse.TaskDescription> getTasks() {
        List<Protocol.ListTasksResponse.TaskDescription> list = new ArrayList<>();
        for (Map.Entry<Integer, TaskDescription> elements : history.entrySet()) {
            list.add(Protocol.ListTasksResponse.TaskDescription.newBuilder()
                    .setClientId(elements.getValue().getClientId())
                    .setResult(elements.getValue().getResult())
                    .setTask(elements.getValue().getTask())
                    .setTaskId(elements.getKey())
                    .build());
        }
        return list;
    }

}

