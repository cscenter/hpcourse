package client;

import communication.Protocol;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Client {
    private final String clientId;
    private final Logger logger;
    private volatile AtomicLong requestCounter = new AtomicLong(0);
    public final HashMap<Long, Protocol.ServerResponse> responses = new HashMap<>();

    private final InputStream input;
    private final OutputStream output;

    private final Object readLock = new Object();
    private final Object writeLock = new Object();

    Client(String clientId, String host, int port) throws IOException {
        this(clientId, host, port, Logger.getLogger(client.Client.class.getName()));
    }

    Client(String clientId, String host, int port, Logger logger) throws IOException {
        this.clientId = clientId;
        this.logger = logger;

        Socket socket = new Socket(host, port);
        this.input = socket.getInputStream();
        this.output = socket.getOutputStream();
    }


    void sendRequest(Protocol.ServerRequest.Builder request) throws IOException {
        request.setClientId(clientId);
        long id = requestCounter.getAndIncrement();
        request.setRequestId(id);
        try {
            synchronized (writeLock) {
                Protocol.WrapperMessage.newBuilder()
                        .setRequest(request)
                        .build()
                        .writeDelimitedTo(output);
            }
            logger.info(MessageFormat.format("client {0}: sendRequest(id={1})", clientId, id));
        } catch (IOException e) {
            logger.log(Level.WARNING
                    , MessageFormat.format("client {0}: exception in sendRequest(id={1}): ", clientId, id)
                    , e);
            throw e;
        }

    }

    Protocol.ServerResponse readResponse() throws IOException {
        Protocol.ServerResponse response;
        try {
            synchronized (readLock) {
                response = Protocol.WrapperMessage.parseDelimitedFrom(input).getResponse();
            }
            Long id = response.getRequestId();
            if (id < 0 || id > requestCounter.get())
                throw new IOException("Impossible requestId: " + id);

            synchronized (responses) {
                responses.put(id, response);
                responses.notifyAll();
            }
            logger.info(MessageFormat.format("client {0}: readResponse, id={1}", clientId, id));
        } catch (IOException e) {
            logger.log(Level.WARNING
                    , MessageFormat.format("client {0}: exception in readResponse(): ", clientId)
                    , e);
            throw e;
        }
        return response;
    }
}
