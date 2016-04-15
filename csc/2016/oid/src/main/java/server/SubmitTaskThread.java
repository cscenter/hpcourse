package server;

import protocol.Protocol;
import task.Param;
import task.Task;

import java.io.IOException;
import java.net.Socket;
import java.security.InvalidParameterException;

public class SubmitTaskThread extends AbstractTaskThread {
    public SubmitTaskThread(Socket socket, Protocol.ServerRequest serverRequest) {
        super(socket, serverRequest);
    }

    @Override
    public void run() {
        Protocol.Task task = serverRequest.getSubmit().getTask();
        Protocol.SubmitTaskResponse.Builder submitTask = Protocol.SubmitTaskResponse.newBuilder();

        try {
            Param a = getParam(task.getA());
            Param b = getParam(task.getB());
            Param p = getParam(task.getP());
            Param m = getParam(task.getM());
            Param n = getParam(task.getN());

            long taskId = TaskManager.addTask(a, b, p, m, n);
            submitTask.setStatus(Protocol.Status.OK);
            submitTask.setSubmittedTaskId(taskId);

        } catch (InvalidParameterException e) {
            submitTask.setStatus(Protocol.Status.ERROR);
        }

        Protocol.ServerResponse.Builder serverResponse = Protocol.ServerResponse.newBuilder();
        serverResponse.setRequestId(serverRequest.getRequestId());
        serverResponse.setSubmitResponse(submitTask.build());


        try {
            sendResponse(serverResponse.build());
        } catch (IOException e) {
            e.printStackTrace();
            try {
                socket.close();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
    }

    private Param getParam(Protocol.Task.Param param) {
        if (param.hasValue()) {
            return Param.newParamWithValue(param.getValue());
        }

        Task parentTask = TaskManager.getTaskById(param.getDependentTaskId());
        if (parentTask == null) {
            throw new InvalidParameterException();
        }

        return Param.newParamWithParentTask(parentTask);
    }
}