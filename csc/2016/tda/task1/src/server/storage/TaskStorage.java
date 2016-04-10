package server.storage;

import server.thread.Calculator;

import java.util.*;

/**
 * @author Dmitriy Tseyler
 */
public class TaskStorage implements Iterable<Map.Entry<Integer, TaskWrapper>> {

    private final Map<Integer, TaskWrapper> threads;

    public TaskStorage() {
        threads = new HashMap<>();
    }

    public synchronized Calculator getCalculator(int id) throws InterruptedException {
        while (!threads.containsKey(id)) {
            wait();
        }
        return threads.get(id).getCalculator();
    }

    public synchronized void add(int taskId, String clientId, Calculator calculator) {
        threads.put(taskId, new TaskWrapper(calculator, clientId));
        notifyAll();
    }

    @Override
    public synchronized Iterator<Map.Entry<Integer, TaskWrapper>> iterator() {
        List<Map.Entry<Integer, TaskWrapper>> list = new ArrayList<>(threads.entrySet());
        return list.iterator();
    }
}
