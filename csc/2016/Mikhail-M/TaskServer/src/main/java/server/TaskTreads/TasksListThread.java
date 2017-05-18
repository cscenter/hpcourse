package server.TaskTreads;

import server.RequestThread;
import communication.Protocol;
import server.TaskManager;

import java.io.IOException;
import java.net.Socket;
import java.util.List;

/* Обработчик запросов на просмотра списка выолняющихся задач*/
public class TasksListThread extends RequestThread {

    public TasksListThread(Socket socket, Protocol.ServerRequest serverRequest, TaskManager taskManager) {
        super(socket, serverRequest, taskManager);
    }

    @Override
    public void run() {
        Protocol.ListTasksResponse.Builder submitTaskResponse = Protocol.ListTasksResponse.newBuilder();

        try {
            List<Protocol.ListTasksResponse.TaskDescription> tasks = taskManager.getTasks();
            submitTaskResponse.addAllTasks(tasks).setStatus(Protocol.Status.OK);
        } catch (Exception e) {
            e.printStackTrace();
            submitTaskResponse.setStatus(Protocol.Status.ERROR);
        }

        Protocol.ServerResponse.Builder serverResponseBuilder = Protocol.ServerResponse.newBuilder();
        serverResponseBuilder.setRequestId(serverRequest.getRequestId());
        serverResponseBuilder.setListResponse(submitTaskResponse.build());

        try {
            send(serverResponseBuilder.build());
        } catch (IOException e) {
            e.printStackTrace();
            try {
                socket.close();
            } catch (IOException e1) {}
        }

    }
}