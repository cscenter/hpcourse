package server.tasks;

import communication.Protocol;
import server.TaskManager;

import java.io.OutputStream;
import java.net.Socket;

public class SubmitTask extends TaskThread {

    public SubmitTask(Socket connectionSocket, Protocol.ServerRequest request, TaskManager taskManager) {
        super(connectionSocket, request, taskManager);
    }

    @Override
    public void run() {
        int taskId = 0;
        Protocol.SubmitTaskResponse.Builder submitTaskResponse = Protocol.SubmitTaskResponse.newBuilder();
        try {
            Protocol.Task task = request.getSubmit().getTask();
            taskId = taskManager.addTask(request.getClientId(), task);
            submitTaskResponse.setSubmittedTaskId(taskId);
            submitTaskResponse.setStatus(Protocol.Status.OK);
        } catch (Exception e) {
            e.printStackTrace();
            submitTaskResponse.setStatus(Protocol.Status.ERROR);
        }

        response.setSubmitResponse(submitTaskResponse);
        super.run();
    }
}
