package com.ashatta.hps.server.internal;

public class CalculationThread extends Thread {
    final private TaskManager taskManager;
    final private Task task;
    final private long submitRequestId;

    public CalculationThread(TaskManager taskManager, Task task, long submitRequestId) {
        this.taskManager = taskManager;
        this.task = task;
        this.submitRequestId = submitRequestId;
    }

    public void run() {
        for (int dependentId : task.dependents()) {
            Task dependent = taskManager.getTask(dependentId);
            synchronized (dependent) {
                if (!dependent.isCompleted()) {
                    try {
                        dependent.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        taskManager.taskSubmitted(task, submitRequestId);

        task.run();

        synchronized (task) {
            taskManager.taskCompleted(task);
            task.notifyAll();
        }

    }
}
