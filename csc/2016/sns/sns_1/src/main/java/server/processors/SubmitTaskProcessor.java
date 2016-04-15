package server.processors;

import communication.Protocol;
import util.ConcurrentStorage;
import util.ProtocolUtils;
import util.TaskAndResult;

import java.net.Socket;
import java.util.logging.Logger;

/**
 * Created by nikita.sokeran@gmail.com
 */
public class SubmitTaskProcessor extends BaseTaskProcessor {

    private static final Logger LOGGER = Logger.getLogger(SubmitTaskProcessor.class.getName());

    private final Protocol.Task task;

    protected SubmitTaskProcessor(final ConcurrentStorage<TaskAndResult> concurrentStorage, final Socket socket, final Protocol.ServerRequest request) {
        super(concurrentStorage, socket, request);
        task = request.getSubmit().getTask();
    }

    private static long calculate(long a, long b, long p, long m, long n) {
        while (n-- > 0) {
            b = (a * p + b) % m;
            a = b;
        }
        return a;
    }

    @Override
    public void run() {
        try {
            final int taskId = (int) concurrentStorage.add(new TaskAndResult(task));
            final Protocol.SubmitTaskResponse submitTaskResponse = ProtocolUtils.createSubmitTaskResponse(taskId, Protocol.Status.OK);
            final Protocol.ServerResponse serverResponse = ProtocolUtils.createServerResponse(request, submitTaskResponse);
            final Protocol.WrapperMessage message = ProtocolUtils.wrapResponse(serverResponse);
            ProtocolUtils.sendWrappedMessage(socket, message);

            final Protocol.Task.Builder taskBuilder = Protocol.Task.newBuilder();

            final long a = getParamValue(task.getA());
            final long b = getParamValue(task.getB());
            final long p = getParamValue(task.getP());
            final long m = getParamValue(task.getM());
            final long n = task.getN();

            final long result = calculate(a, b, p, m, n);

            concurrentStorage.get(taskId).setResult(result);

            LOGGER.info("Params: a=" + a + ", b=" + b + ", p=" + p + ", m=" + m + ", n=" + n + ". Result=" + result);


        } catch (Exception ignored) {

        }
        LOGGER.info("Submit task start processing");
    }

    private long getParamValue(final Protocol.Task.Param param) {
        return param.hasValue()
                ? param.getValue()
                : concurrentStorage.get(param.getDependentTaskId()).getResult();
    }
}