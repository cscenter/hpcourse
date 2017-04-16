package server.processingThreads;

import server.Controller;
import utils.Task;
import communication.Protocol;

import java.io.IOException;
import java.net.Socket;

/* Обработчик запросов на просмотра списка выолняющихся задач*/
public class TasksListProcessingThread extends RequestProcessingThread {

    public TasksListProcessingThread(Socket socket, Protocol.ServerRequest serverRequest) {
        super(socket, serverRequest);
    }

    @Override
    public void run() {
        Protocol.ListTasksResponse.Builder responseBuilder = Protocol.ListTasksResponse.newBuilder();

        // создания списка всех запущенных и выполненных задач
        for (Task currentTask: Controller.getInstance().getTaskList()) {
            Protocol.ListTasksResponse.TaskDescription.Builder builder = Protocol.ListTasksResponse.TaskDescription.newBuilder();
            builder.setTaskId(currentTask.getId());
            builder.setClientId(currentTask.getClientId());
            builder.setTask(currentTask.toProtocolTask());
            if (currentTask.isDone()){
                builder.setResult(currentTask.getResult());
            }
            responseBuilder.addTasks(builder.build());
        }
        responseBuilder.setStatus(Protocol.Status.OK);

        // отправка сообщения кленту
        Protocol.ServerResponse.Builder serverResponseBuilder = Protocol.ServerResponse.newBuilder();
        serverResponseBuilder.setRequestId(serverRequest.getRequestId());
        serverResponseBuilder.setListResponse(responseBuilder.build());

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
