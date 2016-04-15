package server.TaskTreads;


import communication.Protocol;
import server.RequestThread;
import server.TaskManager;

import java.io.IOException;
import java.net.Socket;

public class SubmitTaskThread extends RequestThread {

    public SubmitTaskThread(Socket socket, Protocol.ServerRequest serverRequest, TaskManager taskManager) {
        super(socket, serverRequest, taskManager);
    }

    @Override
    public void run() {
        Protocol.Task currentTask = serverRequest.getSubmit().getTask();
        Protocol.SubmitTaskResponse.Builder builder = Protocol.SubmitTaskResponse.newBuilder();

        try {
            int id = taskManager.submitTask(serverRequest.getClientId(), currentTask);

            builder.setSubmittedTaskId(id);
            builder.setStatus(Protocol.Status.OK);
        } catch (Exception e) {
            builder.setStatus(Protocol.Status.ERROR);
        }

        Protocol.ServerResponse.Builder serverResponseBuilder = Protocol.ServerResponse.newBuilder();
        serverResponseBuilder.setRequestId(serverRequest.getRequestId());
        serverResponseBuilder.setSubmitResponse(builder.build());

        try {
            send(serverResponseBuilder.build());
            socket.close();
        } catch (IOException e1) {}
    }
}

