package server;

import communication.Protocol.Status;
import communication.Protocol.ListTasksResponse.TaskDescription;
import communication.Protocol.ServerResponse;
import communication.Protocol.ListTasksResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by andrey on 13.04.16.
 */
public class ServerListTasksResponse extends AbstractServerResponse{

    public ServerListTasksResponse(long requestID, String clientID) {
        super(requestID, clientID);
    }

    @Override
    protected ServerResponse getResponse() {

        List<TaskDescription> taskDescriptions = new ArrayList<>();

        List<ServerTask> tasks = new ArrayList<>(Server.getTasks().values());
        for (int i = 0; i < tasks.size(); i++) {
            ServerTask task = tasks.get(i);

            TaskDescription.Builder taskDescription = TaskDescription.newBuilder()
                    .setTaskId(task.getID())
                    .setClientId(clientID)
                    .setTask(task.getTask());


            if (task.getStatus() == ServerTask.Status.COMPLETE) {
                taskDescription.setResult(task.getResult());
            }

            taskDescriptions.add(taskDescription.build());
        }

        ListTasksResponse response =  ListTasksResponse.newBuilder()
                .setStatus(Status.OK) // а когда он не ок?
                .addAllTasks(taskDescriptions)
                .build();

        return ServerResponse.newBuilder()
                .setRequestId(requestID)
                .setListResponse(response)
                .build();
    }
}
