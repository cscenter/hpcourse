package com.ashatta.hps.server;

import com.ashatta.hps.communication.Protocol;
import com.ashatta.hps.server.internal.TaskManager;
import com.ashatta.hps.server.internal.Task;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class Server implements Runnable {

    class SocketThread extends Thread {
        private final Socket socket;

        public SocketThread(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try {
                while (true) {
                    InputStream is = socket.getInputStream();
                    Protocol.ServerRequest request = Protocol.WrapperMessage.parseDelimitedFrom(is).getRequest();
                    String clientId = request.getClientId();
                    long requestId = request.getRequestId();
                    requestToSocket.put(requestId, socket);

                    if (request.hasSubmit()) {
                        taskManager.submit(requestId, new Task(clientId, request.getSubmit().getTask(), taskManager));
                    } else if (request.hasSubscribe()) {
                        taskManager.subscribe(requestId, request.getSubscribe().getTaskId());
                    } else if (request.hasList()) {
                        taskManager.listAll(requestId);
                    } else {
                        throw new IllegalArgumentException("Unrecognized request type");
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private final int port;
    private final AtomicBoolean running;
    private TaskManager taskManager;
    private Map<Long, Socket> requestToSocket; // protect

    public Server(int port) {
        this.port = port;
        this.running = new AtomicBoolean(false);
    }

    public void run() {
        if (running.get()) {
            throw new IllegalStateException();
        }

        reinit();
        running.set(true);
        try (ServerSocket listener = new ServerSocket(port)) {
            while (running.get()) {
                Socket socket = listener.accept();
                new SocketThread(socket).start();
            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void stop() {
        running.set(false);

    }

    public void sendSubmitResponse(long requestId, int taskId, boolean status) throws IOException {
        OutputStream os = requestToSocket.get(requestId).getOutputStream();
        Protocol.WrapperMessage message = Protocol.WrapperMessage.newBuilder()
                .setResponse(Protocol.ServerResponse.newBuilder()
                    .setRequestId(requestId)
                    .setSubmitResponse(Protocol.SubmitTaskResponse.newBuilder()
                        .setSubmittedTaskId(taskId)
                        .setStatus(status ? Protocol.Status.OK : Protocol.Status.ERROR))).build();
        message.writeDelimitedTo(os);
    }

    public void sendSubscribeResponse(long requestId, long result, boolean status) throws IOException {
        OutputStream os = requestToSocket.get(requestId).getOutputStream();
        Protocol.WrapperMessage message = Protocol.WrapperMessage.newBuilder()
                .setResponse(Protocol.ServerResponse.newBuilder()
                        .setRequestId(requestId)
                        .setSubscribeResponse(Protocol.SubscribeResponse.newBuilder()
                            .setValue(result)
                            .setStatus(status ? Protocol.Status.OK : Protocol.Status.ERROR))).build();
        message.writeDelimitedTo(os);

    }

    public void sendListTasksResponse(long requestId, List<Task> tasks, boolean status) throws IOException{
        OutputStream os = requestToSocket.get(requestId).getOutputStream();

        List<Protocol.ListTasksResponse.TaskDescription> taskDescriptions = new ArrayList<>();
        for (Task task : tasks) {
            taskDescriptions.add(Protocol.ListTasksResponse.TaskDescription.newBuilder()
                .setClientId(task.getClientId())
                .setResult(task.getResult())
                .setTask(task.getProtoTask())
                .setTaskId(task.getTaskId()).build());
        }
        Protocol.WrapperMessage message = Protocol.WrapperMessage.newBuilder()
                .setResponse(Protocol.ServerResponse.newBuilder()
                        .setRequestId(requestId)
                        .setListResponse(Protocol.ListTasksResponse.newBuilder()
                                .addAllTasks(taskDescriptions)
                                .setStatus(status ? Protocol.Status.OK : Protocol.Status.ERROR))).build();
        message.writeDelimitedTo(os);
    }

    private void reinit() {
        requestToSocket = new HashMap<>();
        taskManager = new TaskManager(this);
    }

    /* Visible for testing. */
    int getPort() {
        return port;
    }
}

