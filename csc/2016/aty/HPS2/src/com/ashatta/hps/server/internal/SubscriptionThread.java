package com.ashatta.hps.server.internal;

public class SubscriptionThread extends Thread {
    final TaskManager taskManager;
    final Task task;
    final long requestId;

    public SubscriptionThread(TaskManager taskManager, Task task, long requestId) {
        this.taskManager = taskManager;
        this.task = task;
        this.requestId = requestId;
    }

    public void run() {
        synchronized (task) {
            while (!task.isCompleted()) {
                try {
                    task.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        taskManager.subscriptionNotify(task, requestId);
    }
}
