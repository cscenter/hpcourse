package ru.compscicenter.ThreadPool;

import java.util.*;

/**
 * Created by Flok on 24.04.2015.
 */

public class FixedThreadPool {
    final private LivingThread[] livingThreads;
    final Set<Object> notifiers = new HashSet();

    final Queue<TaskFuture> futuresQueue = new LinkedList<>();

    public FixedThreadPool(int nThreads) {
        if(nThreads < 1) {
            throw new IllegalArgumentException("Threads amount must be greater than 1");
        }
        livingThreads = new LivingThread[nThreads];
        for(int i = 0; i < nThreads; i++) {
            livingThreads[i] = new LivingThread(this);
            livingThreads[i].start();
        }
    }

    public TaskFuture submit(Task task) {
        if(task == null) {
            throw new IllegalArgumentException("Task can't be null");
        }
        TaskFuture tf = new TaskFuture(task);
        task.setInterruptionMonitor(tf);
        synchronized (futuresQueue) {
            futuresQueue.add(tf);
        }

        wakeUpAllThreads();
        return tf;
    }

    public void stop() {
        for(LivingThread lt : livingThreads) {
            lt.continueWork = false;
            synchronized (lt.currentTaskMonitor) {
                if(lt.currentTask != null) {
                    lt.currentTask.cancel(true);
                }
            }
        }
        wakeUpAllThreads();
    }

    private void wakeUpAllThreads() {
        synchronized (futuresQueue) {
            futuresQueue.notifyAll();
        }
        synchronized (notifiers) {
            for(final Object notifier : notifiers) {
                synchronized (notifier) {
                    notifier.notifyAll();
                }
            }
        }
    }

    public LivingThread[] getLivingThreads() {
        return livingThreads;
    }
}
