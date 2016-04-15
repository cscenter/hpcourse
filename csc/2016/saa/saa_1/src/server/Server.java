/**
 * Created by andrey on 07.04.16.
 */
package server;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import communication.Protocol.*;

public class Server extends Thread {
    // class members
    private static SafeMap<String, Socket> sockets = new SafeMap<>();  // Contains pairs (ClientID, Socket)
    private static SafeMap<Integer, ServerTask> tasks = new SafeMap<>(); // Contains pairs (ServerTaskID, ServerTask)

    // instance fields
    private String clientID;
    private final Socket serverSocket;
    public List<AbstractServerResponse> responses = new ArrayList<>();

    public static void main(String[] args) {
        try {
            startServer();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void startServer() throws IOException {
        ServerSocket server = new ServerSocket(4242);

        while (true) {
            Socket socket = server.accept();
            new Server(socket);
        }
    }

    public static SafeMap<Integer, ServerTask> getTasks() {
        return tasks;
    }

    public static Socket getSocket(String clientID) {
        return sockets.get(clientID);
    }

    Server(Socket serverSocket) {
        this.serverSocket = serverSocket;
        start();
    }

    @Override
    public void run() {
        InputStream inputStream;

        synchronized (serverSocket) {
            try {
                inputStream = serverSocket.getInputStream();
            }
            catch (IOException e) {
                e.printStackTrace();
                try {
                    serverSocket.close();
                }
                catch (IOException IOE) {}
                return;
            }
        }

        try {
            while (true) {
                WrapperMessage message = WrapperMessage.parseDelimitedFrom(inputStream);
                if (message == null) {
                    break;
                }
                ServerRequest request = message.getRequest();
                this.clientID = request.getClientId();

                if (!sockets.containsKey(clientID)) {
                    sockets.put(clientID, serverSocket);
                }

                AbstractServerResponse response;

                if (request.hasSubmit()) {
                    Task task = request.getSubmit().getTask();
                    ServerTask serverTask = new ServerTask(request.getClientId(), task);

                    tasks.put(serverTask.getID(), serverTask);

                    response = new ServerSubmitResponse(request.getRequestId(), serverTask);
                    responses.add(response);

                    serverTask.start(); // starting ServerTask Thread
                    response.start(); // starting SubmitResponse Thread
                }

                if (request.hasSubscribe()) {
                    if (tasks.containsKey(request.getSubscribe().getTaskId())) {
                        response = new ServerSubscribeResponse(
                                request.getRequestId(), tasks.get(request.getSubscribe().getTaskId())
                        );
                        responses.add(response);

                        response.start(); // starting SubscribeResponse Thread
                    }
                }

                if (request.hasList()) {
                    response = new ServerListTasksResponse(request.getRequestId(), request.getClientId());
                    responses.add(response);
                    response.start();
                }

            }
        }
        catch (IOException e) {}
        finally {
            Server.getTasks().values().stream() // waiting for termination of task-threads
                    .forEach(task ->  {
                        if (task.getClientID() == clientID) {
                            try {
                                task.join();
                            }
                            catch (InterruptedException e) {

                            }
                        }
                    });

            responses.stream() // waiting for termination of response-threads
                    .forEach(response ->  {
                        try {
                            response.join();
                        }
                        catch (InterruptedException e) {

                        }
                    });

            System.out.println(clientID + " disconnected");
            sockets.remove(clientID);

            try {
                inputStream.close();
                serverSocket.close();
            }
            catch (IOException e) {}


        }
    }
}
