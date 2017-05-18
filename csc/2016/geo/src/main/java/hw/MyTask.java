package hw;

import communication.Protocol.*;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by Егор on 15.04.2016.
 */
public class MyTask implements Runnable {

    public final Object forSynch = new Object();
    long res = 0;
    private String clientId;
    private Task task;
    private TaskMap taskMap;
    private Status status = Status.RUN;
    private int taskId;

    public MyTask(String clientId, Task task, TaskMap taskManager) {
        this.clientId = clientId;
        this.task = task;
        this.taskMap = taskManager;
    }

    public String getClientId() {
        return clientId;
    }

    public Task getTask() {
        return task;
    }

    public Status getStatus() {
        return status;
    }

    public long getResult() {
        return res;
    }

    @Override
    public void run() {
        try {
            long a = getParam(task.getA()),
                    b = getParam(task.getB()),
                    m = getParam(task.getM()),
                    p = getParam(task.getP()),
                    n = task.getN();
            synchronized (forSynch) {
                System.out.println("Success1");
                work(a, b, p, m, n);
                System.out.println("Success2");
                forSynch.notifyAll();
            }
        } catch (Exception e) {
            //ignore
        }
    }

    private void work(long a, long b, long p, long m, long n) {
        System.out.println("work");
        while (n-- > 0) {
            b = (a * p + b) % m;
            a = b;
        }
        System.out.println("work2");
        res = a;
        status = Status.FINISH;
    }

    private long getParam(Task.Param parameter) throws InterruptedException {
        if (parameter.hasValue())
            return parameter.getValue();
        else
            return taskMap.getTaskResult(parameter.getDependentTaskId());
    }

    @Override
    public String toString() {
        return taskId + ": " + task.toString();
    }

    public enum Status {
        RUN, FINISH
    }
}
