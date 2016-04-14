package server;

import communication.Protocol;

public class Task implements Runnable {

    private long taskId;
    private TaskParameter a;
    private TaskParameter b;
    private TaskParameter m;
    private TaskParameter p;
    private TaskParameter n;

    private String clientId;
    private volatile boolean isReady;
    private volatile long result;

    private Task(TaskParameter a, TaskParameter b, TaskParameter p, TaskParameter m, TaskParameter n, int id, String clientId) {
        this.a = a;
        this.b = b;
        this.p = p;
        this.m = m;
        this.n = n;
        this.taskId = id;
        this.clientId = clientId;
    }


    long calculate(long a, long b, long p, long m, long n) {
        while (n-- > 0) {
            b = (a * p + b) % m;
            a = b;
        }
        return a;
    }

    boolean isReady() {
        return isReady;
    }

    @Override
    public void run() {
        try {
            a.waitResult();
            b.waitResult();
            p.waitResult();
            m.waitResult();
            n.waitResult();
        } catch (InterruptedException e) {
            return;
        }
        long result = calculate(a.getValue(), b.getValue(), p.getValue(), m.getValue(), n.getValue());

        synchronized (this) {
            isReady = true;
            this.result = result;
            this.notifyAll();
        }

    }
}
