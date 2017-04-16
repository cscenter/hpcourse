package server.TaskHelper;


import communication.Protocol;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TaskRepository {
    private static TaskRepository ourInstance = new TaskRepository();

    public static TaskRepository getInstance() {
        return ourInstance;
    }
    public Map <Integer, Task> repository;

    private TaskRepository() {
        repository = new ConcurrentHashMap<Integer, Task>();
    }
    public void putTask(int id, Task task) {
        repository.put(id, task);
    }

    public Protocol.Task getProtoTaskById(int id) {
        return repository.get(id).getProtoTask();
    }

    public Task getTaskById(int id) {
        return repository.get(id);
    }

    public boolean contains(int taskID) {
        return repository.containsKey(taskID);
    }

    public List<Protocol.ListTasksResponse.TaskDescription> getTasksList() {
        List<Protocol.ListTasksResponse.TaskDescription> listTasks = new ArrayList<Protocol.ListTasksResponse.TaskDescription>();

        for (Map.Entry<Integer, Task> taskInfo : repository.entrySet()) {
            Task task = taskInfo.getValue();
            Protocol.ListTasksResponse.TaskDescription.Builder subscribeBuilder = Protocol.ListTasksResponse.TaskDescription.newBuilder();

            subscribeBuilder.setTask(task.getProtoTask());
            subscribeBuilder.setTaskId(taskInfo.getKey());
            if (task.isReady())
                subscribeBuilder.setResult(task.getResult());

            subscribeBuilder.setClientId(task.getClientId());
            listTasks.add(subscribeBuilder.build());
        }

        return listTasks;
    }
}
