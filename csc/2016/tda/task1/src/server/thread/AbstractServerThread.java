package server.thread;

import server.storage.TaskStorage;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.function.BiConsumer;
import java.util.logging.Logger;

import static communication.Protocol.*;

/**
 * Base class for task execution
 * @author Dmitriy Tseyler
 */
class AbstractServerThread<T> extends Thread {
    private static final Logger log = Logger.getLogger(AbstractServerThread.class.getName());

    private final Socket socket;
    private final long requestId;
    private final TaskStorage storage;
    private final String clientId;
    private final BiConsumer<ServerResponse.Builder, T> responseConsumer;

    AbstractServerThread(Socket socket, long requestId, TaskStorage storage, String clientId,
                         BiConsumer<ServerResponse.Builder, T> responseConsumer) {
        this.socket = socket;
        this.requestId = requestId;
        this.storage = storage;
        this.clientId = clientId;
        this.responseConsumer = responseConsumer;
    }

    void response(T message) {
        ServerResponse.Builder builder = ServerResponse.newBuilder().setRequestId(requestId);
        responseConsumer.accept(builder, message);
        ServerResponse response = builder.build();
        try {
            response.writeDelimitedTo(socket.getOutputStream());
        } catch (IOException e) {
            log.warning("Can't send response: " + e.getMessage());
        }
    }

    TaskStorage getStorage() {
        return storage;
    }

    String getClientId() {
        return clientId;
    }
}
