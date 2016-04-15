import communication.Protocol;
import java.util.function.Function;

public class SubmitTaskExecutor {

    private int taskId;

    public SubmitTaskExecutor(int taskId) {
        this.taskId = taskId;
    }

    public long startSubmitTask(final Protocol.Task task) {
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
            Protocol.Task dependentTask = RequestsHistory.getTaskDescriptionById(dependentTaskId).getTask();
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

    private long task(long a, long b, long p, long m, long n) {
        while (n-- > 0) {
            b = (a * p + b) % m;
            a = b;
        }
        return a;
    }

}
