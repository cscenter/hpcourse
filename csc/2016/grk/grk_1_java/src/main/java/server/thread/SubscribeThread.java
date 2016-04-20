package server.thread;

import communication.Protocol;
import server.task.Task;
import server.task.TaskHolder;

import java.io.IOException;
import java.net.Socket;

public class SubscribeThread extends AbstractTaskThread {

    public SubscribeThread(Socket socket, Protocol.ServerRequest serverRequest) {
        super(socket, serverRequest);
    }

    @Override
    public void run() {
        Protocol.SubscribeResponse.Builder builder = Protocol.SubscribeResponse.newBuilder();
        Task currentTask = TaskHolder.getById(serverRequest.getSubscribe().getTaskId());

        checkReady(builder, currentTask);

        sentResponseToClient(builder);

    }

    protected void sentResponseToClient(Protocol.SubscribeResponse.Builder builder) {
        Protocol.ServerResponse.Builder serverResponseBuilder = Protocol.ServerResponse.newBuilder();
        serverResponseBuilder.setRequestId(serverRequest.getRequestId());
        serverResponseBuilder.setSubscribeResponse(builder.build());
        trySendResponse(serverResponseBuilder);
    }

    protected void checkReady(Protocol.SubscribeResponse.Builder builder, Task task) {
        if (task == null) {
            builder.setStatus(Protocol.Status.ERROR);
            return;
        }
        if (!task.isDone) {
            waitTask(task);
        }
        builder.setValue(task.result);
        builder.setStatus(Protocol.Status.OK);

    }

    private void waitTask(Task task) {
        synchronized (task) {
            try {
                task.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
            }
        }
    }
}