package ru.compscicenter.ThreadPool.TestTasks;

import ru.compscicenter.ThreadPool.AbstractTask;

/**
 * Created by Flok on 01.05.2015.
 */
public class SleepingTask extends AbstractTask {
    private final int time;

    public SleepingTask(int time) {
        this.time = time;
    }

    @Override
    public void run() throws InterruptedException {
        System.out.println("Sleeping task started");
        Thread.sleep(time);
        System.out.println("Sleeping task solved");
    }

    @Override
    public Integer getResult() {
        return time;
    }
}
