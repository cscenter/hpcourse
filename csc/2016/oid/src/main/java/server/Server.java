package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class Server {
    private ServerSocket serverSocket;

    public Server() throws IOException {
        serverSocket = new ServerSocket(8080);
    }

    public Server(int port) throws IOException {
        serverSocket = new ServerSocket(port);
    }

    public void start() {
        try {
            while (true) {
                Socket socket = serverSocket.accept();
                new ServerRequestThread(socket).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
