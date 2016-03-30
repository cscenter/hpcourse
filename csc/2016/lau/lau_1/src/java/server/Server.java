package server;

import java.util.ArrayList;
import java.util.List;

public class Server {
    private TaskList taskList;
    private long currentTaskId;
    private List<Thread> threads;

    public Server() {
        taskList = new TaskList();
        threads = new ArrayList<>();
    }

    public long addTask(Task.Type type, long a, long b, long p, long m, long n) {
        Task task = new Task(currentTaskId, type, a, b, p, m, n);

        Thread thread = new Thread(() -> {
            if (task.type == Task.Type.INDEPENDENT) {
                taskList.addIndependentTask(task);
            } else {
                taskList.addDependentTask(task);
            }
        });
        threads.add(thread);
        thread.start();
        return currentTaskId++;
    }

    public long subscribeOnTaskResult(long taskId) {
        return taskList.subscribeOnTaskResult(taskId);
    }

    public List<Task> getTaskList() {
        return taskList.getTasksList();
    }
}