package server.storage;

import server.thread.Calculator;

import java.util.*;

/**
 * @author Dmitriy Tseyler
 */
public class TaskStorage implements Iterable<Map.Entry<Integer, Calculator>> {

    private final Map<Integer, Calculator> threads;

    public TaskStorage() {
        threads = new HashMap<>();
    }

    public synchronized Calculator getCalculator(int id) throws InterruptedException {
        while (!threads.containsKey(id)) {
            wait();
        }
        return threads.get(id);
    }

    public synchronized void add(int taskId, Calculator calculator) {
        threads.put(taskId, calculator);
        notifyAll();
    }

    @Override
    public synchronized Iterator<Map.Entry<Integer, Calculator>> iterator() {
        List<Map.Entry<Integer, Calculator>> list = new ArrayList<>(threads.entrySet());
        return list.iterator();
    }
}
