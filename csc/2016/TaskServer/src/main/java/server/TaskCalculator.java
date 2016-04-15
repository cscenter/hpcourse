package server;

import communication.Protocol;


public class TaskCalculator {

    private TaskRepository taskRepository;


    public TaskCalculator() {
        taskRepository = TaskRepository.getInstance();
    }

    TaskParameter getTaskParameter(Protocol.Task.Param p) {
        if (p.hasDependentTaskId()) {
            return new TaskParameter(taskRepository.getTaskById(p.getDependentTaskId()));
        }
        else
            return new TaskParameter(p.getValue());
    }

   private long calculate(long a, long b, long p, long m, long n) {
        while (n-- > 0) {
            b = (a * p + b) % m;
            a = b;
        }
        return a;
    }

    public void solve(Task task) {
        Protocol.Task protoTask = task.getProtoTask();
        TaskParameter a = getTaskParameter(protoTask.getA());
        TaskParameter b = getTaskParameter(protoTask.getB());
        TaskParameter p = getTaskParameter(protoTask.getP());
        TaskParameter m = getTaskParameter(protoTask.getM());
        TaskParameter n = new TaskParameter(protoTask.getN());
        waitAndGetResult(a, b, p, m, n, task);
    }

    private void waitAndGetResult(TaskParameter a, TaskParameter b, TaskParameter p, TaskParameter m, TaskParameter n, Task task) {

        try {
            a.waitResult();
            b.waitResult();
            p.waitResult();
            m.waitResult();
            n.waitResult();
        }
        catch (InterruptedException e) {

        }
        long result = calculate(a.getValue(), b.getValue(), p.getValue(), m.getValue(), n.getValue());

        synchronized (this) {
            task.setReady(true);
            task.setResult(result);
            this.notifyAll();
        }
    }


}
