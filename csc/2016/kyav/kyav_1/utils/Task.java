package utils;

import communication.Protocol;

/*  Класс задача */
public class Task implements Runnable {
    private int id;
    private volatile boolean isDone;
    private volatile long result;
    private TaskParameter a;
    private TaskParameter b;
    private TaskParameter p;
    private TaskParameter m;
    private TaskParameter n;
    private String clientId;
    private Protocol.Task protoTask;

    public Task(TaskParameter a, TaskParameter b, TaskParameter p, TaskParameter m, TaskParameter n, int id, String clientId) {
        this.a = a;
        this.b = b;
        this.p = p;
        this.m = m;
        this.n = n;
        this.id = id;
        this.clientId = clientId;
        protoTask = null;
    }

    /*  вычисление значения параметров и запуск задачи на выполнение */
    @Override
    public void run() {
        try {
            waitForResult(a);
            waitForResult(b);
            waitForResult(p);
            waitForResult(m);
            waitForResult(n);
        } catch (InterruptedException e) {
            return;
        }
        long result = calculate(a.getValue(), b.getValue(), p.getValue(), m.getValue(), n.getValue());
        synchronized (this) {
            isDone = true;
            this.result = result;
            this.notifyAll();
        }
    }

    /* ожидание готовности переменных */
    private void waitForResult (TaskParameter parameter) throws InterruptedException {
        if (!parameter.isReady()) {
            Task originTask = parameter.getOriginTask();
            synchronized (originTask) {
                while (!originTask.isDone) {
                    originTask.wait();
                }
            }
        }
    }

    /* выполняемая задача */
    private long calculate(long a, long b, long p, long m, long n){
        while (n-- > 0) {
            b = (a * p + b) % m;
            a = b;
        }
        return a;
    }

    /* преобразование в формат сообщения протокола */
    public Protocol.Task toProtocolTask(){
        if (protoTask == null) {
            Protocol.Task.Builder builder = new Protocol.Task.Builder();
            builder.setA(this.a.toProtocolParameter());
            builder.setB(this.b.toProtocolParameter());
            builder.setP(this.p.toProtocolParameter());
            builder.setM(this.m.toProtocolParameter());
            builder.setN(this.n.getValue());
            protoTask = builder.build();
        }
        return protoTask;
    }

    public String getClientId(){
        return clientId;
    }

    public boolean isDone(){
        return isDone;
    }

    public long getResult(){
        return result;
    }

    public int getId() {
        return id;
    }
}
