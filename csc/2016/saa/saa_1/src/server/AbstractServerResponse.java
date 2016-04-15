package server;

import communication.Protocol;
import communication.Protocol.ServerResponse;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

/**
 * Created by andrey on 13.04.16.
 */
public abstract class AbstractServerResponse extends Thread {
    private Socket socket;
    protected long requestID;
    protected String clientID;

    AbstractServerResponse(long requestID, String clientID) {
        this.socket = Server.getSocket(clientID);
        this.clientID = clientID;
        this.requestID = requestID;
    }

    @Override
    public void run() {
        synchronized (socket) {
            try {
                ServerResponse response = getResponse();

                Protocol.WrapperMessage wrapperMessage = Protocol.WrapperMessage
                        .newBuilder()
                        .setResponse(response)
                        .build();

                OutputStream os = socket.getOutputStream();
                wrapperMessage.writeDelimitedTo(os);
                os.flush();

            }
            catch (IOException e) {
                e.printStackTrace();
                System.out.println("Server warning : ServerSubmitResponse IOException");
            }
        }
    }

    protected abstract ServerResponse getResponse();

}
