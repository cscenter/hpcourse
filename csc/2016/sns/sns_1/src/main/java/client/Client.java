package client;

import com.google.protobuf.GeneratedMessage;
import communication.Protocol;
import util.ConcurrentStorage;
import util.FutureValue;
import util.ProtocolUtils;
import util.ValueWrapper;

import java.io.IOException;
import java.net.Socket;
import java.util.logging.Logger;

/**
 * Created by nikita.sokeran@gmail.com
 */
public class Client extends Thread {
    private static final Logger LOGGER = Logger.getLogger(Client.class.getName());

    private final Socket socket;
    private final String clientId;

    private final ConcurrentStorage<ValueWrapper<GeneratedMessage>> futures = new ConcurrentStorage<>();

    public Client(final String host, final int port, final String clientId) throws IOException {
        socket = new Socket(host, port);
        this.clientId = clientId;
    }

    public Protocol.ServerRequest.Builder createServerRequest() {
        return Protocol.ServerRequest.newBuilder()
                .setClientId(clientId);
    }

    @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
    @Override
    public void run() {
        while (!interrupted()) {
            try {
                final Protocol.WrapperMessage wrapperMessage = ProtocolUtils.readWrappedMessage(socket);
                LOGGER.info("Client read wrapped message: " + wrapperMessage);
                if (!wrapperMessage.hasResponse()) {
                    LOGGER.warning("Read message in client, which one isn't an ServerResponse message. Continue");
                    continue;
                }

                final Protocol.ServerResponse response = wrapperMessage.getResponse();
                final long requestId = response.getRequestId();

                if (response.hasSubmitResponse()) {

//                    LOGGER.info("Value wrapper has type: " + Arrays.toString(valueWrapper.getClass().getTypeParameters()));

                    LOGGER.info("Get result:" + response.getSubmitResponse());

                    final ValueWrapper<GeneratedMessage> valueWrapper = futures.get(requestId);
                    synchronized (valueWrapper) {
                        valueWrapper.setValue(response.getSubmitResponse());
                        valueWrapper.notifyAll();
                    }
                }

                if (response.hasSubscribeResponse()) {
                    final ValueWrapper<GeneratedMessage> valueWrapper = futures.get(requestId);
                    synchronized (valueWrapper) {
                        valueWrapper.setValue(response.getSubscribeResponse());
                        valueWrapper.notifyAll();
                    }
                }

                if (response.hasListResponse()) {
                    final ValueWrapper<GeneratedMessage> valueWrapper = futures.get(requestId);
                    synchronized (valueWrapper) {
                        valueWrapper.setValue(response.getListResponse());
                        valueWrapper.notifyAll();
                    }
                }

            } catch (IOException e) {
                LOGGER.warning("Exception " + e + " while reading wrapped message from socket. Continue");
            }
        }
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
        final Protocol.WrapperMessage message = Protocol.WrapperMessage.newBuilder().setRequest(request).build();
        ProtocolUtils.sendWrappedMessage(socket, message);

        return new FutureValue<>(valueWrapper, Protocol.SubmitTaskResponse.class);
    }

    /**
     * @return request id
     * @throws IOException occur when problem with sending task to server
     */
    public long sendServerRequest(final Protocol.Subscribe subscribe) throws IOException {
        final Protocol.ServerRequest request = createServerRequest().setSubscribe(subscribe).build();
        LOGGER.info("Client try to send server request:" + request.getClientId() + " " + request.getRequestId());
        final Protocol.WrapperMessage message = Protocol.WrapperMessage.newBuilder().setRequest(request).build();
        ProtocolUtils.sendWrappedMessage(socket, message);
        final Protocol.WrapperMessage wrapperMessage = ProtocolUtils.readWrappedMessage(socket);
        LOGGER.info("Client retrieve wrapped message:" + wrapperMessage);
        return request.getRequestId();
    }


}
