package server.processors;

import communication.Protocol;
import util.ConcurrentStorage;
import util.TaskAndResult;

import java.net.Socket;

/**
 * Created by nikita.sokeran@gmail.com
 */
public class BaseTaskProcessorFactory {

    private final ConcurrentStorage<TaskAndResult> concurrentStorage;
    private final Socket socket;
    private final Protocol.ServerRequest request;

    public BaseTaskProcessorFactory(final ConcurrentStorage<TaskAndResult> concurrentStorage, final Socket socket, final Protocol.ServerRequest request) {
        this.concurrentStorage = concurrentStorage;
        this.socket = socket;
        this.request = request;
    }

    public BaseTaskProcessor getProcessor() throws NoProcessorForTaskException {
        if (request.hasSubmit()) {
            return new SubmitTaskProcessor(concurrentStorage, socket, request);
        }
        if (request.hasList()) {
            return new ListTasksProcessor(concurrentStorage, socket, request);
        }
        if (request.hasSubscribe()) {
            return new SubscribeProcessor(concurrentStorage, socket, request);
        }

        throw new NoProcessorForTaskException();
    }
}
