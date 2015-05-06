package ru.compscicenter.ThreadPool.TestTasks;

import ru.compscicenter.ThreadPool.AbstractTask;
import ru.compscicenter.ThreadPool.FixedThreadPool;
import ru.compscicenter.ThreadPool.TaskFuture;

/**
 * Created by Flok on 03.05.2015.
 */
public class RecursiveSlowTask extends AbstractTask {
    private final FixedThreadPool fixedThreadPool;
    private int result;
    private final int time;

    public RecursiveSlowTask(FixedThreadPool fixedThreadPool, int time) {
        this.fixedThreadPool = fixedThreadPool;
        this.time = time;
    }

    @Override
    public void run() {
        SleepingTask st = new SleepingTask(time);
        TaskFuture<Integer> tf = fixedThreadPool.submit(st);
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        result = tf.get();
    }

    @Override
    public Integer getResult() {
        return result;
    }
}
