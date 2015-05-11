package ru.csc.concurrent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.FutureTask;

public class SafeQueue {
    private final List<Integer> unsafeQueue = new ArrayList<>();

    public boolean push(Integer task) {
//        TODO: fix it ^_^

        synchronized (unsafeQueue) {
            unsafeQueue.add(task);
            return unsafeQueue.size() == 1;
        }
    }

    public Integer pop() {
        synchronized (unsafeQueue) {
            return !unsafeQueue.isEmpty() ? unsafeQueue.remove(0) : null;
        }
    }
}
