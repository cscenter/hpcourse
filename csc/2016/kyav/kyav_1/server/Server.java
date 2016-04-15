package server;

import server.processingThreads.SubmitTaskProcessingThread;
import server.processingThreads.SubscribeTaskProcessingThread;
import server.processingThreads.TasksListProcessingThread;
import communication.Protocol;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/*  Сервер */
public class Server extends Thread {
    public static final int serverPort = 8080;
    //public static final String serverIP= "localhost";
    private ServerSocket serverSocket;

    public Server() throws IOException {
        serverSocket = new ServerSocket(serverPort);
    }

    @Override
    public void run() {
        try {
            while (true) {
                final Socket newSocket = serverSocket.accept();
                // запуск потока на обработку нового клиента
                new Thread(){
                    @Override
                    public void run() {
                        try {
                            while (true) {
                                int size = 0;
                                size = newSocket.getInputStream().read();
                                byte buf[] = new byte[size];
                                newSocket.getInputStream().read(buf);
                                Protocol.ServerRequest request = Protocol.ServerRequest.parseFrom(buf);;

                                // обработка типа запроса
                                if (request.hasSubmit()) {
                                    new Thread(new SubmitTaskProcessingThread(newSocket, request)).start();
                                }
                                if (request.hasSubscribe()) {
                                    new Thread(new SubscribeTaskProcessingThread(newSocket, request)).start();
                                }
                                if (request.hasList()) {
                                    new Thread(new TasksListProcessingThread(newSocket, request)).start();
                                }
                            }
                        } catch (IOException e) {
                            System.out.println("Error with client communication " + newSocket.getInetAddress());
                            e.printStackTrace();
                        } finally {
                            try {
                                newSocket.close();
                            } catch (IOException e) {}
                        }
                    }
                }.start();
            }
        } catch (IOException e) {
            System.out.println("Error with server socket accept");
            e.printStackTrace();
        }

    }
}
