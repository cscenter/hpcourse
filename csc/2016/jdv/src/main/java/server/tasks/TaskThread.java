package server.tasks;

import communication.Common;
import communication.Protocol;
import server.TaskManager;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

public class TaskThread implements Runnable{

    protected Protocol.ServerRequest request;
    protected Protocol.ServerResponse.Builder response;

    protected TaskManager taskManager;
    private Socket connectionSocket;

    public TaskThread(Socket connectionSocket, Protocol.ServerRequest request, TaskManager taskManager) {
        this.request = request;
        this.taskManager = taskManager;
        this.connectionSocket = connectionSocket;
        response = Protocol.ServerResponse.newBuilder().setRequestId(request.getRequestId());
        response.setRequestId(request.getRequestId());

        Common.printTaskInfo(request);
    }

    @Override
    public void run() {
        try {
            Protocol.WrapperMessage responseMessage = Protocol.WrapperMessage.newBuilder().
                    setResponse(response).
                    build();

            OutputStream out = connectionSocket.getOutputStream();
            responseMessage.writeTo(out);
            out.close();
            connectionSocket.close();

            Common.printTaskRepsonse(responseMessage.getResponse());

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
