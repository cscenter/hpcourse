package server;


import communication.Protocol;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TaskRepository {
    private static TaskRepository ourInstance = new TaskRepository();

    public static TaskRepository getInstance() {
        return ourInstance;
    }
    public Map <Long, Task> repository;

    private TaskRepository() {
        repository = new ConcurrentHashMap<Long, Task>();
    }
    public void putTask(long id, Task task) {
        repository.put(id, task);
    }

    public Protocol.Task getProtoTaskById(long id) {
        return repository.get(id).getProtoTask();
    }

    public Task getTaskById(int id) {
        return repository.get(id);
    }
}
