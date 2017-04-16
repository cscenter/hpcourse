package org.compscicenter.kozmirchuk.server;

import java.util.concurrent.atomic.AtomicBoolean;

public class TaskWrapper {

    final int taskId;
    final Protocol.Task task;
    final String clientId;
    final AtomicBoolean done = new AtomicBoolean(false);
    final Object readyLock = new Object();
    private long result = 0;


    public TaskWrapper(int taskId, Protocol.Task task, String clientId) {
        this.task = task;
        this.clientId = clientId;
        this.taskId = taskId;
    }

    public boolean isDone() {
        return done.get();
    }

    public void run(long a, long b, long p, long m, long n) {

        while(n--> 0) {
            b = (a * p + b) % m;
            a = b;
        }
        result = a;
        done.set(true);
        synchronized (readyLock) {
            readyLock.notifyAll();
        }
    }

    public long getResult() {
        return result;
    }

}
