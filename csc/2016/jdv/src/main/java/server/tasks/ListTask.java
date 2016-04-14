package server.tasks;

import communication.Protocol;
import server.TaskManager;

import java.io.OutputStream;
import java.net.Socket;
import java.util.LinkedList;

public class ListTask extends TaskThread {
    public ListTask(Socket connectionSocket, Protocol.ServerRequest request, TaskManager taskManager) {
        super(connectionSocket, request, taskManager);
    }

    @Override
    public void run() {
        Protocol.ListTasksResponse.Builder submitTaskResponse = Protocol.ListTasksResponse.newBuilder();

        try {
            LinkedList<Protocol.ListTasksResponse.TaskDescription> tasks = taskManager.getTasks();
            submitTaskResponse.addAllTasks(tasks).setStatus(Protocol.Status.OK);
        } catch (Exception e) {
            e.printStackTrace();
            submitTaskResponse.setStatus(Protocol.Status.ERROR);
        }
        response.setListResponse(submitTaskResponse);
        super.run();
    }
}
