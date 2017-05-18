package hw;

import communication.Protocol;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by Егор on 15.04.2016.
 */
public class TaskMap {
    private Map<Integer, MyTask> tasks = new ConcurrentHashMap<>();
    private static AtomicInteger id = new AtomicInteger(1);

    public TaskMap() {

    }

    synchronized public int CreateNewTask(String ClientId, Protocol.Task task) {
        int temp = id.getAndIncrement();
        MyTask tempTask = new MyTask(ClientId, task, this);
        tasks.put(temp, tempTask);
        new Thread(tempTask).start();
        System.out.println("Create: " + tasks.isEmpty());
        return temp;
    }

    public MyTask getTask(int id) {
        return tasks.get(id);
    }

    public long getTaskResult(int id) throws InterruptedException {
        MyTask task = getTask(id);
        System.out.println(tasks.isEmpty());
        synchronized(task.forSynch) {
            while (task.getStatus() != MyTask.Status.FINISH)
                        task.forSynch.wait();
            }
        return task.getResult();
    }



    public Map<Integer, MyTask> getTasks() {
        return tasks;
    }
}
