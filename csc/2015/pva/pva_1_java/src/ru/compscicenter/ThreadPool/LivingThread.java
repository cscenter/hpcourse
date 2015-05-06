package ru.compscicenter.ThreadPool;

import java.util.Stack;

/**
 * Created by Flok on 24.04.2015.
 */
public class LivingThread extends Thread {
    private final FixedThreadPool pool;
    volatile TaskFuture currentTask = null;
    // currentTask может быть null, при этом по переменной нужно синхронизироваться. заводим аналог чистого mutex.
    final Object currentTaskMonitor = new Object();
    private final Stack<TaskFuture> taskStack = new Stack();
    volatile boolean continueWork = true;

    LivingThread(FixedThreadPool pool) {
        this.pool = pool;
    }

    @Override
    public void run() {
        doWork();
    }

    void doWork() {
        doWork(null);
    }

    void doWork(TaskFuture parentTask) {
        while (continueWork) {
            if (parentTask != null && parentTask.isDone()) {
                return;
            }
            TaskWaiter tw = new TaskWaiter(parentTask);
            TaskFuture taskFuture = null;
            try {
                taskFuture = getTask(tw);
            } catch (InterruptedException e) {
                // interrupted in 'wait'
            }
            if (taskFuture == null) {
                if (parentTask == null) {
                    continue;
                } else {
                    return;
                }
            }
            currentTask = taskFuture;
            taskStack.push(currentTask);
            taskFuture.run();
            synchronized (currentTaskMonitor) {
                currentTask = taskStack.isEmpty() ? null : taskStack.peek();
                synchronized (taskFuture) {
                    taskFuture.notifyAll();
                }
            }
        }
    }

    private class TaskWaiter {
        final TaskFuture tf;
        final Object notifier;

        public TaskWaiter(TaskFuture tf) {
            this.tf = tf;
            notifier = tf != null ? tf : pool.futuresQueue;
        }

        public boolean needContinue() {
            return tf == null || !tf.isDone();
        }

        public final Object getNotifier() {
            return notifier;
        }
    }

    private TaskFuture getTask(TaskWaiter tw) throws InterruptedException {
        TaskFuture ft;
        while (tw.needContinue() && continueWork) {
            Object notifier = tw.getNotifier();
            synchronized (pool.futuresQueue) {
                if (!pool.futuresQueue.isEmpty()) {
                    ft = pool.futuresQueue.poll();
                    return ft;
                } else {
                    if (notifier == pool.futuresQueue) {
                        pool.futuresQueue.wait();
                    }
                }
            }

            if (notifier != pool.futuresQueue) {
                synchronized (pool.notifiers) {
                    pool.notifiers.add(notifier);
                }
                synchronized (notifier) {
                    notifier.wait();
                }
                synchronized (pool.notifiers) {
                    pool.notifiers.remove(notifier);
                }
            }
        }
        return null;
    }
}
