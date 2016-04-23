package utils;

import communication.Protocol;

public class SubmitTaskExecutor {

    public long startSubmitTask(final Protocol.Task task) throws IllegalArgumentException {
        long a = getParam(task.getA());
        long b = getParam(task.getB());
        long p = getParam(task.getP());
        long m = getParam(task.getM());
        long n = task.getN();
        return task(a, b, p, m, n);
    }

    private long getParam(final Protocol.Task.Param param) throws IllegalArgumentException {
        if (param.hasValue()) {
            return param.getValue();
        } else {
            int dependentTaskId = param.getDependentTaskId();
            TaskDescription dependentTaskDescription = RequestsHistory.getTaskDescriptionById(dependentTaskId);
            if (dependentTaskDescription == null) {
                throw new IllegalArgumentException();
            }
            Protocol.Task dependentTask = dependentTaskDescription.getTask();
            synchronized (dependentTask) {
                while (!RequestsHistory.getTaskDescriptionById(dependentTaskId).isDone()) {
                    try {
                        dependentTask.wait();
                    } catch (InterruptedException e) {}
                }
                return RequestsHistory.getTaskDescriptionById(dependentTaskId).getResult();
            }
        }
    }

    private long task(long a, long b, long p, long m, long n) throws IllegalArgumentException {
        if (m == 0) {
            throw new IllegalArgumentException();
        }
        while (n-- > 0) {
            b = (a * p + b) % m;
            a = b;
        }
        return a;
    }

}
