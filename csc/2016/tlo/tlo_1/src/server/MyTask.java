/**
 * Created by andrey on 07.04.16.
 */
package server;

import communication.Protocol.Task;

import java.util.concurrent.atomic.AtomicInteger;

public class MyTask extends Thread {

    private static final AtomicInteger generate_id = new AtomicInteger(0);
    private final int id;
    private Task task;
    private Parameter a;
    private Parameter b;
    private Parameter p;
    private Parameter m;
    private long n;
    private long result;
    private Status taskStatus = Status.START;
    public MyTask(Task task) {
        id = generate_id.getAndIncrement();
        this.a = new Parameter(task.getA());
        this.b = new Parameter(task.getB());
        this.p = new Parameter(task.getP());
        this.m = new Parameter(task.getM());
        this.n = task.getN();
        this.task = task;
    }

    public Status getTaskStatus() {
        return taskStatus;
    }

    public long getResult() {
        return result;
    }

    public int getTaskId() {
        return id;
    }

    public Parameter.ParamStatus checkParam(Parameter param) {
        param.waitAndSetParameter();
        if (param.getStatus() == Parameter.ParamStatus.ERROR) {
            taskStatus = Status.ERROR;
            return Parameter.ParamStatus.ERROR;
        }
        return param.getStatus();
    }

    public Task getTask() {
        return task;
    }

    @Override
    public void run() {
        if (checkParam(a) == Parameter.ParamStatus.ERROR) {
            return;
        }
        if (n == 0) {
            if (a.getStatus() == Parameter.ParamStatus.READY) {
                result = a.getValue();
                taskStatus = Status.COMPLETED;
            } else {
                taskStatus = Status.ERROR;
                return;
            }
        }


        if (checkParam(p) == Parameter.ParamStatus.ERROR ||
                checkParam(m) == Parameter.ParamStatus.ERROR ||
                checkParam(b) == Parameter.ParamStatus.ERROR) {
            return;
        }

        if (checkParam(m) == Parameter.ParamStatus.ERROR) {
            return;
        }


        long bValue = b.getValue(), aValue = a.getValue();
        while (n-- > 0) {
            bValue = (aValue * p.getValue() + bValue) % m.getValue();
            aValue = bValue;
        }

        result = aValue;
        taskStatus = Status.COMPLETED;
    }

    public static enum Status {
        RUNNING, ERROR, COMPLETED, START;
    }

}
