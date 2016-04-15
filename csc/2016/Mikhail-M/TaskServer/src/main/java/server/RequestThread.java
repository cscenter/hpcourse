package server;

import communication.Protocol;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

public abstract class RequestThread implements Runnable{
    protected Socket socket;
    protected Protocol.ServerRequest serverRequest;
    protected TaskManager taskManager;

    public RequestThread(Socket socket, Protocol.ServerRequest serverRequest, TaskManager taskManager){
        this.socket = socket;
        this.serverRequest = serverRequest;
        this.taskManager = taskManager;
    }
    protected void send(Protocol.ServerResponse serverResponse) throws IOException {
        OutputStream out = null;
        out = socket.getOutputStream();
        synchronized (out){
            out.write(serverResponse.getSerializedSize());
            serverResponse.writeTo(out);
            out.flush();
        }
    }
}