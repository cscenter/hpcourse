package client;

import com.google.protobuf.GeneratedMessage;
import communication.Protocol;
import util.*;

import java.io.Closeable;
import java.io.IOException;
import java.net.Socket;
import java.util.logging.Logger;

/**
 * Created by nikita.sokeran@gmail.com
 */
public final class Client implements Closeable {
    private static final Logger LOGGER = Logger.getLogger(Client.class.getName());

    private final Socket socket;
    private final String clientId;
    private final Thread listener = new Thread(new SocketListener());
    private final ConcurrentStorage<ValueWrapper<GeneratedMessage>> futures = new ConcurrentStorage<>();

    public Client(final String host, final int port, final String clientId) throws IOException {
        socket = new Socket(host, port);
        this.clientId = clientId;
        listener.start();
    }

    public Protocol.ServerRequest.Builder createServerRequest() {
        return Protocol.ServerRequest.newBuilder()
                .setClientId(clientId);
    }


    /**
     * @return request id
     * @throws IOException occur when problem with sending task to server
     */
    public FutureValue<Protocol.SubmitTaskResponse> sendServerRequest(final Protocol.SubmitTask submitTask) throws IOException {
        final ValueWrapper<GeneratedMessage> valueWrapper = new ValueWrapper<>(Protocol.SubmitTaskResponse.class);
        final long requestId = futures.add(valueWrapper);

        final Protocol.ServerRequest request = createServerRequest().setRequestId(requestId).setSubmit(submitTask).build();
        LOGGER.info("Client try to send server request:" + request.getClientId() + " " + request.getRequestId());
        final Protocol.WrapperMessage message = ProtocolUtils.wrapRequest(request);
        ProtocolUtils.sendWrappedMessage(socket, message);

        return new FutureValue<>(valueWrapper, Protocol.SubmitTaskResponse.class);
    }

    /**
     * @return request id
     * @throws IOException occur when problem with sending task to server
     */
    public FutureValue<Protocol.SubscribeResponse> sendServerRequest(final Protocol.Subscribe subscribe) throws IOException {
        final ValueWrapper<GeneratedMessage> valueWrapper = new ValueWrapper<>(Protocol.SubscribeResponse.class);
        final long requestId = futures.add(valueWrapper);

        final Protocol.ServerRequest request = createServerRequest().setRequestId(requestId).setSubscribe(subscribe).build();
        LOGGER.info("Client try to send server request:" + request.getClientId() + " " + request.getRequestId());
        final Protocol.WrapperMessage message = ProtocolUtils.wrapRequest(request);
        ProtocolUtils.sendWrappedMessage(socket, message);

        return new FutureValue<>(valueWrapper, Protocol.SubscribeResponse.class);
    }

    public FutureValue<Protocol.ListTasksResponse> sendServerRequest(final Protocol.ListTasks listTasks) throws IOException {
        final ValueWrapper<GeneratedMessage> valueWrapper = new ValueWrapper<>(Protocol.ListTasksResponse.class);
        final long requestId = futures.add(valueWrapper);

        final Protocol.ServerRequest request = createServerRequest().setRequestId(requestId).setList(listTasks).build();
        LOGGER.info("Client try to send server request:" + request.getClientId() + " " + request.getRequestId());
        final Protocol.WrapperMessage message = ProtocolUtils.wrapRequest(request);
        ProtocolUtils.sendWrappedMessage(socket, message);

        return new FutureValue<>(valueWrapper, Protocol.ListTasksResponse.class);
    }

    @Override
    public void close() throws IOException {
        socket.close();
        listener.interrupt();
    }

    private class SocketListener implements Runnable {
        @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    final Protocol.WrapperMessage wrapperMessage = ProtocolUtils.readWrappedMessage(socket);

                    if (wrapperMessage == null) {
                        continue;
                    }

                    LOGGER.info("Client read wrapped message: " + wrapperMessage);
                    if (!wrapperMessage.hasResponse()) {
                        LOGGER.warning("Read message in client, which one isn't an ServerResponse message. Continue");
                        continue;
                    }

                    final Protocol.ServerResponse response = wrapperMessage.getResponse();
                    final long requestId = response.getRequestId();

                    final ValueWrapper<GeneratedMessage> valueWrapper = futures.get(requestId);

                    if (valueWrapper == null) {
                        LOGGER.warning("Receive message from server with wrong request id");
                        continue;
                    }

                    if (response.hasSubmitResponse()) {
                        synchronized (valueWrapper) {
                            try {
                                valueWrapper.setValue(response.getSubmitResponse());
                            } catch (CheckedClassCastException e) {
                                LOGGER.warning("Server answer is wrong, expected that value wrapper store SubmitTaskResponse");
                            }
                        }
                    }

                    if (response.hasSubscribeResponse()) {
                        synchronized (valueWrapper) {
                            try {
                                valueWrapper.setValue(response.getSubscribeResponse());
                            } catch (CheckedClassCastException e) {
                                LOGGER.warning("Server answer is wrong, expected that value wrapper store SubscribeResponse");
                            }
                        }
                    }

                    if (response.hasListResponse()) {
                        synchronized (valueWrapper) {
                            try {
                                valueWrapper.setValue(response.getListResponse());
                            } catch (CheckedClassCastException e) {
                                LOGGER.warning("Server answer is wrong, expected that value wrapper store ListTasksResponse");
                            }
                        }
                    }

                } catch (IOException e) {
                    LOGGER.warning("Exception " + e + " while reading wrapped message from socket. Continue. Client id: " + clientId);
                } catch (InterruptedException e) {
                    LOGGER.info("Client listener was interrupted");
                    //Ignored because all close logic will be executed right after this block
                }
            }
            LOGGER.info("Socket was interrupted, try close socket");
            try {
                socket.close();
                LOGGER.info("Socket for client " + clientId + " was closed");
            } catch (IOException e) {
                LOGGER.warning("Can't close socket. Client id: " + clientId);
            }
        }
    }
}
