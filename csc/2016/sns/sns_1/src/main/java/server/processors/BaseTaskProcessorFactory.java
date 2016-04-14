package server.processors;

import communication.Protocol;

import java.net.Socket;

/**
 * Created by nikita.sokeran@gmail.com
 */
public class BaseTaskProcessorFactory {

    private final Socket socket;
    private final Protocol.ServerRequest request;

    public BaseTaskProcessorFactory(final Socket socket, final Protocol.ServerRequest request) {
        this.socket = socket;
        this.request = request;
    }

    public BaseTaskProcessor getProcessor() throws NoProcessorForTaskException {
        if (request.hasSubmit()) {
            return new SubmitTaskProcessor(socket, request);
        }
        if (request.hasList()) {
            return new ListTasksProcessor(socket, request);
        }
        if (request.hasSubscribe()) {
            return new SubscribeProcessor(socket, request);
        }

        throw new NoProcessorForTaskException();
    }
}
