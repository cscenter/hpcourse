package server.thread;

import server.storage.TaskStorage;

import static communication.Protocol.*;

import java.net.Socket;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Thread for submit task
 * @author Dmitriy Tseyler
 */
public class Calculator extends AbstractServerThread<SubmitTaskResponse> {

    private static final Logger log = Logger.getLogger(Calculator.class.getName());

    private final Task task;
    private final int taskId;

    private Status status;
    private long result;

    Calculator(Socket socket, long requestId, Task task, TaskStorage storage, int taskId, String clientId) {
        super(socket, requestId, storage, clientId, ServerResponse.Builder::setSubmitResponse);
        this.task = task;
        this.taskId = taskId;

        status = Status.OK;
    }

    @Override
    public void run() {
        try {
            long a = get(task::getA);
            long b = get(task::getB);
            long p = get(task::getP);
            long m = get(task::getM);
            long n = task.getN();
            response(SubmitTaskResponse.newBuilder()
                    .setStatus(status)
                    .setSubmittedTaskId(taskId)
                    .build());
            result = calculate(a, b, p, m, n);
        } catch (InterruptedException e) {
            status = Status.ERROR;
            log.warning("Can't calculate task: " + e.getMessage());
        }
    }

    private long calculate(long a, long b, long p, long m, long n) {
        while (n-- > 0) {
            b = (a * p + b) % m;
            a = b;
        }
        return a;
    }

    Status getStatus() throws InterruptedException {
        join();
        return status;
    }

    long getValue() throws InterruptedException {
        join();
        return result;
    }

    Task getTask() {
        return task;
    }

    private long get(Supplier<Task.Param> supplier) throws InterruptedException {
        Task.Param param = supplier.get();
        if (param.hasValue()) return param.getValue();

        // prevent deadlock
        if (param.getDependentTaskId() > taskId) throw new InterruptedException("Can't wait task with larger id");

        Calculator calculator = getStorage().getCalculator(param.getDependentTaskId());
        Status status = calculator.getStatus();
        if (status == Status.ERROR) throw new InterruptedException();

        return calculator.getValue();
    }
}
