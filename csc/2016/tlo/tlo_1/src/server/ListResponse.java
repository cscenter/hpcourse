package server;

import communication.Protocol;
import communication.Protocol.ServerResponse;
import communication.Protocol.ListTasksResponse;
import communication.Protocol.ListTasksResponse.TaskDescription;

import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by lt on 11.05.16.
 */
public class ListResponse extends AbstractResponse {

    private MyTask task;

    public ListResponse(Socket socket, long requestId, String clientId) {
        super(socket, requestId, clientId);
    }

    public ServerResponse get() {
        List<ListTasksResponse.TaskDescription> tasksList = new ArrayList<>();
        for (int i = 0; i < Server.tasks.size(); ++i) {
            MyTask task = Server.tasks.get(i);
            ListTasksResponse.TaskDescription.Builder taskDescription = TaskDescription.newBuilder();
            taskDescription
                    .setTask(task.getTask())
                    .setTaskId(task.getTaskId())
                    .setClientId(clientId);
            if (task.getTaskStatus() == MyTask.Status.COMPLETED) {
                taskDescription.setResult(task.getResult());
            }
            tasksList.add(taskDescription.build());
        }

        if (tasksList.size() == 0) {
            System.out.println("Empty tasks");
        }

        return ServerResponse
                .newBuilder()
                .setListResponse(
                        ListTasksResponse
                            .newBuilder()
//                            .setTasks(0, tasksList.)
                            .addAllTasks(tasksList)
                            .setStatus(Protocol.Status.OK)
                            .build()
                )
                .setRequestId(requestId)
                .build();
    }
}
