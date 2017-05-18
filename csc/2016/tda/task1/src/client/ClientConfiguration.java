package client;

import client.params.MessageParams;
import com.google.protobuf.GeneratedMessage;

import static communication.Protocol.*;

/**
 * @author Dmitriy Tseyler
 */
class ClientConfiguration {
    private final int type;
    private final MessageParams<?> params;
    private final long requestId;
    private final String clientId;

    ClientConfiguration(int type, MessageParams<?> params, long requestId, String clientId) {
        this.type = type;
        this.params = params;
        this.requestId = requestId;
        this.clientId = clientId;
    }

    ServerRequest create() {
        MessageManager manager = MessageManager.of(type);
        GeneratedMessage message = manager.generate(params);
        ServerRequest.Builder request = ServerRequest.newBuilder().setRequestId(requestId).setClientId(clientId);
        manager.setTask(request, message);
        return request.build();
    }

    String toText(ServerResponse response) {
        MessageManager manager = MessageManager.of(type);
        return manager.getText(response, clientId);
    }
}
