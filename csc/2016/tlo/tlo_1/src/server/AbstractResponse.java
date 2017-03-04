package server;

import communication.Protocol.ServerResponse;
import communication.Protocol.WrapperMessage;

import java.io.IOException;
import java.net.Socket;

/**
 * Created by lt on 11.05.16.
 */
public abstract class AbstractResponse extends Thread {
    protected long requestId;
    protected String clientId;
    private Socket socket;

    protected AbstractResponse(Socket socket, long requestId, String clientId) {
        this.socket = socket;
        this.requestId = requestId;
        this.clientId = clientId;
    }

    protected AbstractResponse(Socket socket, long requestId) {
        this.socket = socket;
        this.requestId = requestId;
    }

    abstract ServerResponse get();

    @Override
    public void run() {
        ServerResponse response = get();
        WrapperMessage msg = WrapperMessage
                .newBuilder()
                .setResponse(response)
                .build();

        try {
            msg.writeDelimitedTo(socket.getOutputStream());
        } catch (IOException e) {
            e.getStackTrace();
        }
    }


}
