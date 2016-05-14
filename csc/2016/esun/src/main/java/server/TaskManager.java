package server;

import communication.Protocol;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by Helen on 10.04.2016.
 */
public class TaskManager {
    private Map<Integer, Task> taskMap = new ConcurrentHashMap<>();
    private int LastID = 0;

    public int CreateTask(String ClientID, Protocol.Task taskInfo) {
        int id = LastID++;
        Task task = new Task(ClientID, taskInfo);
        taskMap.put(id, task);
        return id;
    }

    public Task getTask(int id){
        if(!taskMap.containsKey(id)){
            throw new IndexOutOfBoundsException();
        }
        else
            return taskMap.get(id);
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

    public Map<Integer, Task> getTaskMap(){
        return taskMap;
    }
}
