package ru.csc.concurrent;

import java.util.LinkedList;

public class ThreadPoolQueue {
    private final LinkedList<ThreadPoolTask<?>> queue = new LinkedList<>();
    private final Object queueLock = new Object();

    public ThreadPoolQueue() {
    }

    public void push(ThreadPoolTask<?> task) {
        synchronized (queueLock) {
            queue.add(task);
        }
    }

    public ThreadPoolTask<?> popIfExists() {
        synchronized (queueLock) {
            return !queue.isEmpty() ? queue.pop() : null;
        }
    }

    ThreadPoolTask<?> getTask(int taskId) {
        synchronized (queueLock) {
            for (ThreadPoolTask<?> task : queue) {
                if (task.getTaskId() == taskId) {
                    return task;
                }
            }
        }

        return null;
    }


}
