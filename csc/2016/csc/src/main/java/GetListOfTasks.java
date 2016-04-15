import communication.Protocol;

import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.stream.Collectors;

//это мы писали вместе, только сейчас это выглядит красиво

public class GetListOfTasks extends BaseTask {

    private HashMap<Integer, SubmitTask> mapOfTasks;

    GetListOfTasks(Socket socket, long requestId, HashMap<Integer, SubmitTask> mapOfTasks) {
        super(socket, requestId);
        this.mapOfTasks = mapOfTasks;
        start();
    }

    @Override
    public void run() {
        Protocol.ListTasksResponse.Builder builder = Protocol.ListTasksResponse.newBuilder();
        builder.setStatus(Protocol.Status.OK);
        builder.addAllTasks(getAllTasks());
        sendResponse(builder.build());
    }

    private ArrayList<Protocol.ListTasksResponse.TaskDescription> getAllTasks() {
        ArrayList<Protocol.ListTasksResponse.TaskDescription> listOfTasks = new ArrayList<>();

        synchronized (mapOfTasks) {
            listOfTasks.addAll(mapOfTasks.keySet()
                    .stream()
                    .map(key -> getTaskDescription(mapOfTasks.get(key)))
                    .collect(Collectors.toList()));
        }

        return listOfTasks;
    }

    private Protocol.ListTasksResponse.TaskDescription getTaskDescription(SubmitTask task) {
        Protocol.ListTasksResponse.TaskDescription.Builder builder =
                Protocol.ListTasksResponse.TaskDescription.newBuilder();
        builder.setTask(task.getTask());
        builder.setClientId(task.getClientId());
        if(task.getStatus() == Protocol.Status.OK) {
            builder.setResult(task.getResult());
        }
        builder.setTaskId(task.getTaskId());
        return builder.build();
    }
}
