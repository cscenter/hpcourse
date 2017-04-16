package server.tasks;

import communication.Protocol;
import server.TaskManager;

import java.io.OutputStream;
import java.net.Socket;

public class SubscribeTask extends TaskThread {

    public SubscribeTask(Socket connectionSocket, Protocol.ServerRequest request, TaskManager taskManager) {
        super(connectionSocket, request, taskManager);
    }

    @Override
    public void run() {
        long result = 0;
        Protocol.SubscribeResponse.Builder subscribeTaskResponse = Protocol.SubscribeResponse.newBuilder();
        try {
            result = taskManager.getResult(request.getSubscribe().getTaskId());
            subscribeTaskResponse.setValue(result);
            subscribeTaskResponse.setStatus(Protocol.Status.OK);
        } catch (Exception e) {
            e.printStackTrace();
            subscribeTaskResponse.setStatus(Protocol.Status.ERROR);
        }
        response.setSubscribeResponse(subscribeTaskResponse);
        super.run();
    }
}
