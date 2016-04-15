package server;

import task.Param;
import task.Task;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class TaskManager {
    private static AtomicLong id = new AtomicLong(0);
    private static Map<Long, Task> taskMap = Collections.synchronizedMap(new HashMap<Long, Task>());

    public static long addTask(Param a, Param b, Param p, Param m, Param n) {
        long taskId = id.incrementAndGet();
        Task task = Task.newTask(taskId, a, b, p, m, n);

        taskMap.put(taskId, task);

        task.start();
        return taskId;
    }

    public static ArrayList<Task> getTasks() {
        return new ArrayList<>(taskMap.values());
    }

    public static Task getTaskById(long id) {
        return taskMap.get(id);
    }
}
