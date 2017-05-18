package server.processors;

import communication.Protocol;
import util.ConcurrentStorage;
import util.Functions;
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


    @Override
    public void run() {
        Long result = null;
        final int taskId = (int) concurrentStorage.add(new TaskAndResult(task, request.getClientId()));
        try {
            final Protocol.SubmitTaskResponse submitTaskResponse = ProtocolUtils.createSubmitTaskResponse(taskId, Protocol.Status.OK);
            final Protocol.ServerResponse serverResponse = ProtocolUtils.createServerResponse(request)
                    .setSubmitResponse(submitTaskResponse)
                    .build();
            final Protocol.WrapperMessage message = ProtocolUtils.wrapResponse(serverResponse);
            ProtocolUtils.sendWrappedMessage(socket, message);

            final long a = getParamValue(task.getA());
            final long b = getParamValue(task.getB());
            final long p = getParamValue(task.getP());
            final long m = getParamValue(task.getM());
            final long n = task.getN();

            result = Functions.calculateModulo(a, b, p, m, n);
            LOGGER.info("Params: a=" + a + ", b=" + b + ", p=" + p + ", m=" + m + ", n=" + n + ". Result=" + result);

        } catch (Exception ignored) {

        } finally {
            if (result == null) {
                concurrentStorage.get(taskId).setResult(Protocol.Status.ERROR, null);
            } else {
                concurrentStorage.get(taskId).setResult(Protocol.Status.OK, result);
            }
        }
        LOGGER.info("Submit task start processing");
    }

    private long getParamValue(final Protocol.Task.Param param) {
        return param.hasValue()
                ? param.getValue()
                : concurrentStorage.get(param.getDependentTaskId()).getResult();
    }
}