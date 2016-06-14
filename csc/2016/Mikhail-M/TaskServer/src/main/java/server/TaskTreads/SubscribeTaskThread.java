
package server.TaskTreads;

import communication.Protocol;
import server.TaskManager;

import java.io.IOException;
import java.net.Socket;

/*  Обработчик запросов на подписку на результат запущенной задачи*/
public class SubscribeTaskThread extends server.RequestThread {

    public SubscribeTaskThread(Socket socket, Protocol.ServerRequest serverRequest, TaskManager taskManager) {
        super(socket, serverRequest, taskManager);
    }

    @Override
    public void run() {
        long result = 0;
        Protocol.SubscribeResponse.Builder builder = Protocol.SubscribeResponse.newBuilder();
        try {
            result = taskManager.getResult(serverRequest.getSubscribe().getTaskId());
            builder.setValue(result);
            builder.setStatus(Protocol.Status.OK);
        } catch (Exception e) {
            e.printStackTrace();
            builder.setStatus(Protocol.Status.ERROR);
        }


        Protocol.ServerResponse.Builder serverResponseBuilder = Protocol.ServerResponse.newBuilder();
        serverResponseBuilder.setRequestId(serverRequest.getRequestId());
        serverResponseBuilder.setSubscribeResponse(builder.build());

        try {
            send(serverResponseBuilder.build());
        } catch (IOException e) {
            e.printStackTrace();
            try {
                socket.close();
            } catch (IOException e1) {
            }
        }

    }

}
