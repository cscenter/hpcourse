package server;

import protocol.Protocol;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

public abstract class AbstractTaskThread extends Thread {
    protected Socket socket;
    protected Protocol.ServerRequest serverRequest;

    public AbstractTaskThread(Socket socket, Protocol.ServerRequest serverRequest) {
        this.socket = socket;
        this.serverRequest = serverRequest;
    }

    protected void sendResponse(Protocol.ServerResponse serverResponse) throws IOException {
        try (OutputStream os = socket.getOutputStream()) {
            synchronized (os) {
                os.write(serverResponse.getSerializedSize());
                serverResponse.writeTo(os);
                os.flush();
            }
        }
    }
}
