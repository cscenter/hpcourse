package server.thread;

import communication.Protocol;
import server.task.Parameter;
import server.task.TaskHolder;

import java.net.Socket;

import static server.utils.Converter.convertToParameter;

public class SubmitTaskThread extends AbstractTaskThread {

    public SubmitTaskThread(Socket socket, Protocol.ServerRequest serverRequest) {
        super(socket, serverRequest);
    }

    @Override
    public void run() {
        Protocol.Task task = serverRequest.getSubmit().getTask();
        Protocol.SubmitTaskResponse.Builder builder = Protocol.SubmitTaskResponse.newBuilder();
        runTask(task, builder);
        sentResponse(builder);
    }

    protected void sentResponse(Protocol.SubmitTaskResponse.Builder builder) {
        Protocol.ServerResponse.Builder serverResponseBuilder = Protocol.ServerResponse.newBuilder();
        serverResponseBuilder.setRequestId(serverRequest.getRequestId());
        serverResponseBuilder.setSubmitResponse(builder.build());
        trySendResponse(serverResponseBuilder);
    }

    protected void runTask(Protocol.Task task, Protocol.SubmitTaskResponse.Builder builder) {
        try {
            Parameter a = convertToParameter(task.getA());
            Parameter b = convertToParameter(task.getB());
            Parameter p = convertToParameter(task.getP());
            Parameter m = convertToParameter(task.getM());
            Parameter n = new Parameter(task.getN());
            int currentTaskId = TaskHolder.submit(serverRequest.getClientId(), a, b, p, m, n);
            builder.setSubmittedTaskId(currentTaskId);
            builder.setStatus(Protocol.Status.OK);
        } catch (Exception e) {
            e.printStackTrace();
            builder.setStatus(Protocol.Status.ERROR);
        }
    }


}