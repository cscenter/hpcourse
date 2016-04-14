package server.processors;

import communication.Protocol;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.logging.Logger;

/**
 * Created by nikita.sokeran@gmail.com
 */
public abstract class BaseTaskProcessor implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(BaseTaskProcessor.class.getName());

    protected Socket socket;
    protected Protocol.ServerRequest request;

    protected BaseTaskProcessor(final Socket socket, final Protocol.ServerRequest request) {
        this.socket = socket;
        this.request = request;
    }

    protected void sendResponse(final Protocol.ServerResponse response) {
        try {
            final OutputStream outputStream = socket.getOutputStream();
            final Protocol.WrapperMessage message = Protocol.WrapperMessage.newBuilder().setResponse(response).build();
            outputStream.write(message.getSerializedSize());
            message.writeTo(outputStream);
        } catch (IOException e) {
            LOGGER.warning("Can't get socket's output stream: " + e);
        }
    }


}
