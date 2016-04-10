package server.thread;

import server.storage.TaskStorage;
import server.storage.TaskWrapper;

import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static communication.Protocol.*;

/**
 * @author Dmitriy Tseyler
 */
class StatusResponser extends AbstractServerThread<ListTasksResponse> {

    private static final Logger log = Logger.getLogger(StatusResponser.class.getName());

    StatusResponser(Socket socket, long requestId, TaskStorage storage) {
        super(socket, requestId, storage, ServerResponse.Builder::setListResponse);
    }

    @Override
    public void run() {
        TaskStorage storage = getStorage();
        ListTasksResponse.Builder builder = ListTasksResponse.newBuilder();
        builder.setStatus(Status.OK);
        List<ListTasksResponse.TaskDescription> resultList = new ArrayList<>();
        try {
            for (Map.Entry<Integer, TaskWrapper> entry : storage) {
                Calculator calculator = entry.getValue().getCalculator();
                ListTasksResponse.TaskDescription.Builder descriptionBuilder = ListTasksResponse.TaskDescription
                        .newBuilder()
                        .setTaskId(entry.getKey())
                        .setClientId(entry.getValue().getClientId())
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
