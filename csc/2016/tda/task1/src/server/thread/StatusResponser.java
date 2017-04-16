package server.thread;

import server.storage.TaskStorage;

import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static communication.Protocol.*;

/**
 * Thread for list task
 * @author Dmitriy Tseyler
 */
class StatusResponser extends AbstractServerThread<ListTasksResponse> {

    private static final Logger log = Logger.getLogger(StatusResponser.class.getName());

    StatusResponser(Socket socket, long requestId, String clientId, TaskStorage storage) {
        super(socket, requestId, storage, clientId, ServerResponse.Builder::setListResponse);
    }

    @Override
    public void run() {
        TaskStorage storage = getStorage();
        ListTasksResponse.Builder builder = ListTasksResponse.newBuilder();
        builder.setStatus(Status.OK);
        List<ListTasksResponse.TaskDescription> resultList = new ArrayList<>();
        try {
            for (Map.Entry<Integer, Calculator> entry : storage) {
                Calculator calculator = entry.getValue();
                ListTasksResponse.TaskDescription.Builder descriptionBuilder = ListTasksResponse.TaskDescription
                        .newBuilder()
                        .setTaskId(entry.getKey())
                        .setClientId(calculator.getClientId())
                        .setTask(calculator.getTask());
                if (!calculator.isAlive() && calculator.getStatus() == Status.OK) {
                    descriptionBuilder.setResult(calculator.getValue());
                }
                resultList.add(descriptionBuilder.build());
            }
            builder.addAllTasks(resultList);
        } catch (InterruptedException e) {
            log.warning("Can't get list of tasks: " + e.getMessage());
            builder.setStatus(Status.ERROR);
        }

        response(builder.build());
    }
}
