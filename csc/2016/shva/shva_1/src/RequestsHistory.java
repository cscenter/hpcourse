import communication.Protocol;

import java.util.*;

public class RequestsHistory {

    private static Map<Integer, TaskDescription> history
            = Collections.synchronizedMap(new HashMap<Integer, TaskDescription>());

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

class TaskDescription {

    private String clientId;
    private boolean isDone = false;
    private Protocol.Task task;
    private Optional<Protocol.Status> status;
    private Optional<Long> result;

    public TaskDescription(String clientId, Protocol.Task task) {
        this.clientId = clientId;
        this.task = task;
    }

    public TaskDescription setResult(Optional<Long> result) {
        this.result = result;
        return this;
    }

    public TaskDescription setStatus(Optional<Protocol.Status> status) {
        this.status = status;
        return this;
    }

    public TaskDescription setDone() {
        isDone = true;
        return this;
    }

    public boolean isDone() {
        return isDone;
    }

    public Protocol.Task getTask() {
        return task;
    }

    public long getResult() {
        return result.get();
    }

    public Protocol.Status getStatus() {
        return status.get();
    }

    public String getClientId() {
        return clientId;
    }

}
