package server.processors;

import communication.Protocol;
import javafx.util.Pair;
import util.ConcurrentStorage;
import util.ProtocolUtils;
import util.TaskAndResult;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * @author nikita.sokeran@emc.com
 */
public class ListTasksProcessor extends BaseTaskProcessor {

    private static final Logger LOGGER = Logger.getLogger(ListTasksProcessor.class.getName());


    protected ListTasksProcessor(final ConcurrentStorage<TaskAndResult> concurrentStorage,
                                 final Socket socket,
                                 final Protocol.ServerRequest request) {
        super(concurrentStorage, socket, request);
    }

    @Override
    public void run() {

        final List<Pair<Long, TaskAndResult>> tasks = concurrentStorage.getContent();

        final List<Protocol.ListTasksResponse.TaskDescription> taskDescriptions = new ArrayList<>();

        for (final Pair<Long, TaskAndResult> task : tasks) {
            final Protocol.ListTasksResponse.TaskDescription.Builder taskDescriptionBuilder = Protocol.ListTasksResponse.TaskDescription.newBuilder()
                    .setTaskId(task.getKey().intValue())
                    .setClientId(task.getValue().getClientId())
                    .setTask(task.getValue().getTask());


            if (task.getValue().hasStatus()) {
                taskDescriptionBuilder.setResult(task.getValue().getResult());
            }

            taskDescriptions.add(taskDescriptionBuilder.build());
        }

        final Protocol.ListTasksResponse.Builder listTasksResponseBuilder = Protocol.ListTasksResponse.newBuilder();

        listTasksResponseBuilder
                .setStatus(Protocol.Status.OK);

        taskDescriptions.forEach(listTasksResponseBuilder::addTasks);

        final Protocol.ServerResponse serverResponse = ProtocolUtils.createServerResponse(request)
                .setListResponse(listTasksResponseBuilder.build())
                .build();

        final Protocol.WrapperMessage wrapperMessage = ProtocolUtils.wrapResponse(serverResponse);

        try {
            ProtocolUtils.sendWrappedMessage(socket, wrapperMessage);
        } catch (IOException e) {
            LOGGER.warning("Can't send wrapped message to client");
        }

    }
}
