package communication;

import server.TaskOrganizer;

public class Task implements Runnable {
    private Parameter a, b, p, m;
    private long n;
    private int id;
    private String clientId;
    private Protocol.Task protocolTask;
    private volatile boolean isDone;
    private volatile long result;

    public Task(Parameter a, Parameter b, Parameter p, Parameter m, long n, int id, String clientId) {
        this.a = a;
        this.b = b;
        this.p = p;
        this.m = m;
        this.n = n;
        this.id = id;
        this.clientId = clientId;
    }

    public Task(Protocol.Task task, int id, String clientId) {
        this.a = createParam(task.getA());
        this.b = createParam(task.getB());
        this.p = createParam(task.getP());
        this.m = createParam(task.getM());
        this.n = task.getN();
        this.id = id;
        this.clientId = clientId;
    }

    private Parameter createParam(Protocol.Task.Param param) {
        return param.hasValue() ? new Parameter(param.getValue()) :
                new Parameter(TaskOrganizer.getTask(param.getDependentTaskId()));
    }

    @Override
    public void run() {
        long result = calculate(a.getValue(), b.getValue(), p.getValue(), m.getValue(), n);
        synchronized (this) {
            isDone = true;
            this.result = result;
            this.notifyAll();
        }
    }

    private static long calculate(long a, long b, long p, long m, long n) {
        while (n-- > 0) {
            b = (a * p + b) % m;
            a = b;
        }
        return a;
    }

    public Protocol.Task toProtocolTask() {
        if (protocolTask == null) {
            Protocol.Task.Builder builder = Protocol.Task.newBuilder();
            builder.setA(toProtocolTaskParam(a));
            builder.setB(toProtocolTaskParam(b));
            builder.setP(toProtocolTaskParam(p));
            builder.setM(toProtocolTaskParam(m));
            builder.setN(n);
            protocolTask = builder.build();
        }
        return protocolTask;
    }

    private static Protocol.Task.Param toProtocolTaskParam(Parameter a) {
        Protocol.Task.Param.Builder builder = Protocol.Task.Param.newBuilder();
        builder.setValue(a.getValue());
        return builder.build();
    }

    public String getClientId() {
        return clientId;
    }

    public boolean isDone() {
        return isDone;
    }

    public long getResult() {
        while (!isDone) {
            try {
                synchronized (this) {
                    wait();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return result;
    }

    public int getId() {
        return id;
    }
}
