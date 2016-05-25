package server.processingThreads;

import communication.Protocol;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
/*  Базовый класс для обработчиков запросов клиентов*/

public abstract class RequestProcessingThread implements Runnable{
    protected Socket socket;
    protected Protocol.ServerRequest serverRequest;

    public RequestProcessingThread(Socket socket, Protocol.ServerRequest serverRequest){
        this.socket = socket;
        this.serverRequest = serverRequest;
    }
    protected void send(Protocol.ServerResponse serverResponse) throws IOException {
        OutputStream out = null;
        out = socket.getOutputStream();
        synchronized (out){
//            out.write(serverResponse.getSerializedSize());
//            serverResponse.writeTo(out);
            serverResponse.writeDelimitedTo(out);
            out.flush();
        }

    }
}
