package task;

import protocol.Protocol;

public class Task extends Thread {
    private long id;
    private Param a;
    private Param b;
    private Param p;
    private Param m;
    private Param n;

    private volatile boolean ready;
    private volatile long result;

    private Task(long id, Param a, Param b, Param p, Param m, Param n) {
        this.id = id;
        this.a = a;
        this.b = b;
        this.p = p;
        this.m = m;
        this.n = n;
    }

    public static Task newTask(long id, Param a, Param b, Param p, Param m, Param n) {
        return new Task(id, a, b, p, m, n);
    }

    public long getTaskId() {
        return id;
    }

    public boolean isReady() {
        return ready;
    }

    public long getResult() {
        return result;
    }

    @Override
    public void run() {
        try {
            solveParam(a);
            solveParam(b);
            solveParam(p);
            solveParam(m);
            solveParam(n);
        } catch (InterruptedException e) {
            return;
        }

        long result = solve(a.getValue(), b.getValue(), p.getValue(), m.getValue(), n.getValue());

        synchronized (this) {
            this.ready = true;
            this.result = result;
            this.notifyAll();
        }
    }

    private long solve(long a, long b, long p, long m, long n) {
        while (n-- > 0) {
            b = (a * p + b) % m;
            a = b;
        }

        return a;
    }

    private void solveParam(Param param) throws InterruptedException {
        Task parentTask = param.getParentTask();
        if (parentTask == null) {
            return;
        }
        synchronized (parentTask) {
            while (!parentTask.isReady()) {
                parentTask.wait();
            }
        }
    }


    public Protocol.Task toProtocolTask() {
        Protocol.Task.Builder task = Protocol.Task.newBuilder();
        task.setA(a.toProtocolParam());
        task.setB(b.toProtocolParam());
        task.setP(p.toProtocolParam());
        task.setM(m.toProtocolParam());
        task.setN(n.toProtocolParam());

        return task.build();
    }
}
