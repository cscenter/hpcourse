package ru.compscicenter.ThreadPool.TestTasks;

import ru.compscicenter.ThreadPool.AbstractTask;
import ru.compscicenter.ThreadPool.FixedThreadPool;
import ru.compscicenter.ThreadPool.TaskFuture;

/**
 * Created by Flok on 02.05.2015.
 */
public class RecursiveTask extends AbstractTask {
    private final FixedThreadPool fixedThreadPool;
    private int result;
    private final int time;

    public RecursiveTask(FixedThreadPool fixedThreadPool, int time) {
        this.fixedThreadPool = fixedThreadPool;
        this.time = time;
    }

    @Override
    public void run() {
        SleepingTask st = new SleepingTask(time);
        TaskFuture<Integer> tf = fixedThreadPool.submit(st);
        result = tf.get();
    }

    @Override
    public Integer getResult() {
        return result;
    }
}
