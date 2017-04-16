package server;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;


public class TaskStore {
    private HashMap<Integer, ClientTask> taskStore;
    private HashMap<Integer, Object> taskLock;

    public TaskStore () {
        taskStore = new HashMap<>();
        taskLock = new HashMap<>();
    }

    public boolean contains(int taskId) {
        return taskStore.containsKey(taskId);
    }

    public boolean isSolved(int taskId) {
        return taskStore.get(taskId).isSolved();
    }

    public synchronized void addTask(int taskId, ClientTask clientTask) {
        taskStore.put(taskId, clientTask);
        taskLock.put(taskId, new Object());
    }

    public Set<Map.Entry<Integer, ClientTask> > entrySet() {
        return taskStore.entrySet();
    }

    public long getResult(int taskId) throws InterruptedException {
        synchronized (taskLock.get(taskId)) {
            while (!isSolved(taskId))
                taskLock.get(taskId).wait();
        }
        return taskStore.get(taskId).getResult();
    }

    public synchronized void updateResult(int taskId, long result) {
        taskStore.get(taskId).updateSolution(result);
        taskLock.get(taskId).notifyAll();
    }

    public Object getMonitor(int taskId) {
        return taskLock.get(taskId);
    }
}
