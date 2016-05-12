/**
 * Created by andrey on 07.04.16.
 */
package server;

import communication.Protocol.ServerRequest;
import communication.Protocol.Task;
import communication.Protocol.WrapperMessage;
import util.ConcurrentMap;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class Server extends Thread {
    public static ConcurrentMap<Integer, MyTask> tasks = new ConcurrentMap<>();
    private static ConcurrentMap<Server, Socket> serverThreads = new ConcurrentMap<>(); //initial capacity
    private static ConcurrentMap<String, List<AbstractResponse>> responses = new ConcurrentMap<>();
    private Socket serverSocket;


    public Server(Socket s) {
        serverThreads.put(this, s);
        serverSocket = s;

    }

    public static void main(String[] args) {
        try {
            ServerSocket server = new ServerSocket(1234);
            while (true) {
                Socket socket = server.accept();
                Server newS = new Server(socket);
                newS.start();

            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return;
    }

    @Override
    public void run() {
        InputStream inputStream = null;
        ServerRequest request = null;
        try {
            inputStream = serverSocket.getInputStream();
            while (true) {
                WrapperMessage msg = WrapperMessage.parseDelimitedFrom(inputStream);
                if (msg == null) {
                    break;
                }

                AbstractResponse response = null;
                if (msg.hasRequest()) {
                    request = msg.getRequest();
                    if (request.hasSubmit()) {
                        System.out.println("Has task");
                        Task task = request.getSubmit().getTask();
                        MyTask newTask = new MyTask(task);
                        tasks.put(newTask.getTaskId(), newTask);
                        newTask.start();
                        response = new SubmitResponse(newTask, serverSocket, request.getRequestId(), request.getClientId());

                    }

                    if (request.hasSubscribe()) {
                        int taskId = request.getSubscribe().getTaskId();
                        MyTask subscribeTask;
                        if (tasks.containsKey(taskId)) {
                            subscribeTask = tasks.get(taskId);
                        } else {
                            subscribeTask = null;
                        }

                        response = new SubscribeResponse(subscribeTask, serverSocket, request.getRequestId());
                    }

                    if (request.hasList()) {
                        response = new ListResponse(serverSocket, request.getRequestId(), request.getClientId());
                    }

                    if (response != null) {
                        response.start();
                        if (!responses.containsKey(request.getClientId())) {
                            responses.put(request.getClientId(), new ArrayList<AbstractResponse>());
                        } else {
                            responses.get(request.getClientId()).add(response);
                        }
                    }
                }

            }


        } catch (IOException e) {
            e.getStackTrace();
        } finally {
            List<AbstractResponse> clientReponses = responses.get(request.getClientId());
            for (AbstractResponse response : clientReponses) {
                try {
                    response.join();
                } catch (InterruptedException e) {
                    e.getStackTrace();
                }

            }
            try {
                inputStream.close();
                serverThreads.remove(this);
            } catch (IOException e) {
                e.getStackTrace();
            }
        }
    }

}

