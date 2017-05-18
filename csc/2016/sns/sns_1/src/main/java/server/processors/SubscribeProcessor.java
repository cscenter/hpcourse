package server.processors;

import communication.Protocol;
import util.ConcurrentStorage;
import util.ProtocolUtils;
import util.TaskAndResult;

import java.io.IOException;
import java.net.Socket;
import java.util.logging.Logger;

/**
 * @author nikita.sokeran@emc.com
 */
public class SubscribeProcessor extends BaseTaskProcessor {
    private static final Logger LOGGER = Logger.getLogger(SubscribeProcessor.class.getName());

    protected SubscribeProcessor(final ConcurrentStorage<TaskAndResult> concurrentStorage, final Socket socket, final Protocol.ServerRequest request) {
        super(concurrentStorage, socket, request);
    }

    @Override
    public void run() {
        final int taskId = request.getSubscribe().getTaskId();
        final TaskAndResult taskAndResult = concurrentStorage.get(taskId);

        final Protocol.SubscribeResponse.Builder subscribeResponseBuilder = Protocol.SubscribeResponse.newBuilder();
        subscribeResponseBuilder.setStatus(taskAndResult.getStatus());

        if (taskAndResult.getStatus() == Protocol.Status.OK) {
            subscribeResponseBuilder.setValue(taskAndResult.getResult());
        }

        final Protocol.SubscribeResponse subscribeResponse = Protocol.SubscribeResponse.newBuilder()
                .setStatus(taskAndResult.getStatus())
                .setValue(taskAndResult.getResult())
                .build();

        final Protocol.ServerResponse serverResponse = ProtocolUtils.createServerResponse(request)
                .setSubscribeResponse(subscribeResponse)
                .build();

        final Protocol.WrapperMessage wrapperMessage = ProtocolUtils.wrapResponse(serverResponse);
        try {
            ProtocolUtils.sendWrappedMessage(socket, wrapperMessage);
        } catch (IOException e) {
            LOGGER.warning("Can't send wrapped message to client");
        }
    }
}
