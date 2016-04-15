
package server;

import communication.Protocol;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/*  Сервер */
public class Server extends Thread {
    public static final int serverPort = 8080;

    private ServerSocket serverSocket;

    public Server() throws IOException {
        serverSocket = new ServerSocket(serverPort);
    }

    @Override
    public void run() {
        try {
            while (true) {
                final Socket newSocket = serverSocket.accept();

                new Thread() {
                    @Override
                    public void run() {
                        try {
                            TaskManager taskManager = new TaskManager();
                            while (true) {
                                int size = 0;
                                size = newSocket.getInputStream().read();
                                byte buf[] = new byte[size];
                                newSocket.getInputStream().read(buf);
                                Protocol.ServerRequest request = Protocol.ServerRequest.parseFrom(buf);

                                if (request.hasSubmit()) {
                                    new Thread(new server.SubmitTaskThread(newSocket, request, taskManager)).start();
                                }
                                if (request.hasSubscribe()) {
                                    new Thread(new server.SubscribeTaskThread(newSocket, request, taskManager)).start();
                                }
                                if (request.hasList()) {
                                    new Thread(new server.TasksListThread(newSocket, request, taskManager)).start();
                                }
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        } finally {
                            try {
                                newSocket.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }.start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
