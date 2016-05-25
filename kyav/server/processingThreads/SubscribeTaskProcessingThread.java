package server.processingThreads;

import server.Controller;
import utils.Task;
import communication.Protocol;

import java.io.IOException;
import java.net.Socket;

/*  Обработчик запросов на подписку на результат запущенной задачи*/
public class SubscribeTaskProcessingThread extends RequestProcessingThread {

    public SubscribeTaskProcessingThread(Socket socket, Protocol.ServerRequest serverRequest) {
        super(socket, serverRequest);
    }

    @Override
    public void run() {
        Protocol.SubscribeResponse.Builder builder = Protocol.SubscribeResponse.newBuilder();
        int currentTaskId = serverRequest.getSubscribe().getTaskId();
        Task currentTask = Controller.getInstance().getTaskById(currentTaskId);

        // проверка готовности задачи, подписка на результат
        if (currentTask == null) {
            builder.setStatus(Protocol.Status.ERROR);
        } else {
            if (!currentTask.isDone()) {
                synchronized (currentTask) {
                    while (!currentTask.isDone()) {
                        try {
                            currentTask.wait();
                        } catch (InterruptedException e) {
                            System.out.println("Thread interrupted");
                            e.printStackTrace();
                            try {
                                socket.close();
                            } catch (IOException e1) {
                            }
                        }
                    }
                }
            }
            builder.setValue(currentTask.getResult());
            builder.setStatus(Protocol.Status.OK);
        }

        // отправка собщения клиенту
        Protocol.ServerResponse.Builder serverResponseBuilder = Protocol.ServerResponse.newBuilder();
        serverResponseBuilder.setRequestId(serverRequest.getRequestId());
        serverResponseBuilder.setSubscribeResponse(builder.build());

        try {
            send(serverResponseBuilder.build());
        } catch (IOException e) {
            System.out.println("Error sending data to " + socket.getInetAddress());
            e.printStackTrace();
            try {
                socket.close();
            } catch (IOException e1) {}
        }

    }

}
