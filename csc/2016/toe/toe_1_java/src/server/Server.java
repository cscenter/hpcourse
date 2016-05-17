package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import static communication.Protocol.ServerRequest;
import static communication.Protocol.WrapperMessage;

public class Server extends Thread {
    public static final int PORT = 8081;

    @Override
    public void run() {
        try {
            ServerSocket serverSocket = new ServerSocket(PORT);
            while (true) {
                Socket socket = serverSocket.accept();
                new Thread(new ServerProcess(socket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class ServerProcess implements Runnable {
        Socket socket;

        public ServerProcess(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                while (true) {
                    ServerRequest request = WrapperMessage.parseDelimitedFrom(socket.getInputStream()).getRequest();

                    if (request.hasSubmit()) {
                        new Thread(new TaskOrganizer.SubmittingThread(socket, request)).start();
                    } else if (request.hasSubscribe()) {
                        new Thread(new TaskOrganizer.SubscribingThread(socket, request)).start();
                    } else if (request.hasList()) {
                        new Thread(new TaskOrganizer.ListingThread(socket, request)).start();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
