package ru.compscicenter.ThreadPool.TestTasks;

import ru.compscicenter.ThreadPool.AbstractTask;
import ru.compscicenter.ThreadPool.InterruptionMonitor;

/**
 * Created by Flok on 01.05.2015.
 */
public class CountingTask extends AbstractTask {
    private final int countLimits;
    private InterruptionMonitor monitor;

    public CountingTask() {
        this(10000000);
    }

    public CountingTask(int countLimits) {
        this.countLimits = 10000000;
    }

    @Override
    public void run() {
        System.out.println("Counting task started");
        for(int j = 0; j < countLimits; j++){
            for (int i = 0; i < countLimits; i++) {
                if(monitor.isInterrupted()) {
                    return;
                }
                if(i == j) {
                    i++;
                }
            }
        }
        System.out.println("Counting task solved");
    }

    @Override
    public void setInterruptionMonitor(InterruptionMonitor monitor) {
        this.monitor = monitor;
    }
}
