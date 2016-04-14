import communication.Protocol;
import communication.Protocol.Task;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class TaskManager {
    private Map<Long, Protocol.Task> tasks = new ConcurrentHashMap<Long, Protocol.Task>();
    private AtomicLong taskIdCounter;

    TaskManager() {
        taskIdCounter.set(0);
    }

    public long submitTask(String ClientID, Task task) {
        long id = taskIdCounter.getAndIncrement();
        //tasks.put(id, server.Task(task));
        return id;
    }


    public Protocol.SubscribeResponse getSubscribeResponseById(long id) {
        return null;
    }

    public Task getTaskById(int id) {
        if (tasks.containsKey(id)) {
            return tasks.get(id);
        }
        return null;
    }


}
