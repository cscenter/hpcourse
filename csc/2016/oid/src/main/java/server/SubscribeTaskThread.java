package server;

import protocol.Protocol;
import task.Task;

import java.io.IOException;
import java.net.Socket;

public class SubscribeTaskThread extends AbstractTaskThread {
    public SubscribeTaskThread(Socket socket, Protocol.ServerRequest serverRequest) {
        super(socket, serverRequest);
    }

    @Override
    public void run() {
        long taskId = serverRequest.getSubscribe().getTaskId();
        Protocol.SubscribeTaskResponse.Builder subscribeTask = Protocol.SubscribeTaskResponse.newBuilder();

        Task task = TaskManager.getTaskById(taskId);

        if (task == null) {
            subscribeTask.setStatus(Protocol.Status.ERROR);
        } else {
            if (!task.isReady()) {
                try {
                    task.wait();
                } catch (InterruptedException e) {
                    try {
                        socket.close();
                    } catch (IOException ignored) {
                    }
                }
            }

            subscribeTask.setStatus(Protocol.Status.OK);
            subscribeTask.setValue(task.getResult());
        }

        Protocol.ServerResponse.Builder serverResponse = Protocol.ServerResponse.newBuilder();
        serverResponse.setRequestId(serverRequest.getRequestId());
        serverResponse.setSubscribeResponse(subscribeTask.build());

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
}
