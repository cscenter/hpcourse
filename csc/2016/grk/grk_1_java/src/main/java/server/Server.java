package server;

import communication.Protocol;
import server.thread.SubmitTaskThread;
import server.thread.SubscribeThread;
import server.thread.TasksListThread;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/** Just server. nothing interesting for comment  */
public class Server extends Thread {
    public static final int PORT = 8081;
    private ServerSocket server;

    public Server() throws IOException {
        server = new ServerSocket(PORT);
    }

    @Override
    public void run() {
        try {
            while (true) {
                final Socket socket = server.accept();
                processRequest(socket);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    protected void processRequest(final Socket socket) throws IOException {
        try {
            while (true) {
                int size = socket.getInputStream().read();
                byte buf[] = new byte[size];
                size = socket.getInputStream().read(buf);
                Protocol.ServerRequest request = Protocol.ServerRequest.parseFrom(buf);
                processRequest(request, socket);
            }
        } finally {
            socket.close();

        }
    }

    protected void processRequest(Protocol.ServerRequest request, Socket newSocket) {
        if (request.hasSubmit()) {
            new Thread(new SubmitTaskThread(newSocket, request)).start();
        } else if (request.hasSubscribe()) {
            new Thread(new SubscribeThread(newSocket, request)).start();
        } else if (request.hasList()) {
            new Thread(new TasksListThread(newSocket, request)).start();
        } else {
            throw new ServerException("unknown task");
        }
    }

    public class ServerException extends RuntimeException {
        public ServerException(String message) {
            super(message);
        }
    }
}