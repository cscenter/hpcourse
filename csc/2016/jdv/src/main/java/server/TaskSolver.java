package server;

import communication.Protocol;

public class TaskSolver {

    private TaskStore taskStore;

    public TaskSolver(TaskStore taskStore) {
        this.taskStore = taskStore;
    }


    private boolean haveDependentAndIsNotSolved(Protocol.Task.Param param) {
        return param.hasDependentTaskId() && !taskStore.isSolved(param.getDependentTaskId());
    }

    private boolean notReady(ClientTask clientTask) {
        Protocol.Task task = clientTask.getTask();
        return haveDependentAndIsNotSolved(task.getA()) && haveDependentAndIsNotSolved(task.getB()) &&
                haveDependentAndIsNotSolved(task.getM()) && haveDependentAndIsNotSolved(task.getP());
    }

    private long solve(long a, long b, long p, long m, long n) {
        while (n-- > 0) {
            b = (a * p + b) % m;
            a = b;
        }
        return a;
    }

    private long getValue(Protocol.Task.Param param) throws InterruptedException {
        if (param.hasDependentTaskId())
            return taskStore.getResult(param.getDependentTaskId());
        return param.getValue();
    }

    public long solveTask(ClientTask clientTask) throws InterruptedException {
        synchronized (taskStore.getMonitor(clientTask.getTaskId())) {

            notReady(clientTask);

            Protocol.Task task = clientTask.getTask();
            long result = solve(getValue(task.getA()), getValue(task.getB()),
                    getValue(task.getP()), getValue(task.getM()), task.getN());

            taskStore.updateResult(clientTask.getTaskId(), result);
            return result;
        }
    }
}
