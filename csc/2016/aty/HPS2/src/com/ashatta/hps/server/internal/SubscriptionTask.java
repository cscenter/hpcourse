package com.ashatta.hps.server.internal;

/* Implements a subscription request. Waits for completion of the specified tasks, then calls back the TaskManager. */
public class SubscriptionTask implements Runnable {
    final TaskManager taskManager;
    final CalculationTask task;
    final long requestId;

    public SubscriptionTask(TaskManager taskManager, CalculationTask task, long requestId) {
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
