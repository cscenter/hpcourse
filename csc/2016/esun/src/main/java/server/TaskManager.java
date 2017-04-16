package server;

import communication.Protocol;
import javafx.util.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.ObjDoubleConsumer;
import java.util.stream.Collectors;

/**
 * Created by Helen on 10.04.2016.
 */
public class TaskManager {
    private Map<Integer, Task> taskMap = new HashMap<>();
    private final Object locker = new Object();
    private int LastID = 0;

    public int CreateTask(String ClientID, Protocol.Task taskInfo) {
        synchronized (locker) {
            int id = LastID++;
            Task task = new Task(ClientID, taskInfo);
            taskMap.put(id, task);
            return id;
        }
    }

    public Task getTask(int id){
        synchronized (locker) {
            if (!taskMap.containsKey(id)) {
                throw new IndexOutOfBoundsException();
            } else
                return taskMap.get(id);
        }
    }

    public boolean hasTask(int id){
        synchronized (locker){
            return taskMap.containsKey(id);
        }
    }

    public long getTaskResult(int id) throws InterruptedException {
        Task task = getTask(id);
        Object monitor = task.getMonitor();
        synchronized(monitor){
            while (task.getState() != Task.State.FINISHED)
                monitor.wait();
        }
        return task.result.get();
    }

    public List<Pair<Integer, Task>> getTaskMap()
    {
        synchronized (locker) {
            List<Pair<Integer, Task>> res = taskMap.entrySet().stream().map(e ->
                    new Pair<>(e.getKey(), e.getValue())).collect(Collectors.toList());
            return res;
        }
    }
}
