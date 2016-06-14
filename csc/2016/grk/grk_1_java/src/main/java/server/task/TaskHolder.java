package server.task;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** just singleton */
public class TaskHolder {

    private AtomicInteger idCounter  = new AtomicInteger(0);
    private Map <Integer, Task> taskMap = new ConcurrentHashMap<>();

    private static TaskHolder taskHolder = null;

    private synchronized static TaskHolder getInstance(){
        if (taskHolder == null) {
            taskHolder = new TaskHolder();
        }
        return taskHolder;
    }

    public static ArrayList<Task> getTaskList(){
        return new ArrayList<>(getInstance().taskMap.values());
    }

    public static Task getById(int id){
        return TaskHolder.getInstance().taskMap.get(id);
    }

    private int submitTask(String clientId, Parameter a, Parameter b, Parameter p, Parameter m, Parameter n){
        int currentId = idCounter.incrementAndGet();
        Task newTask = new Task(a, b, p, m, n, currentId, clientId);
        taskMap.put(currentId, newTask);
        new Thread(newTask).start();
        return currentId;
    }

    public static int submit(String clientId, Parameter a, Parameter b, Parameter p, Parameter m, Parameter n) {
        return getInstance().submitTask(clientId,a,b,p,m,n);
    }


}