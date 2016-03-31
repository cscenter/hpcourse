package server;

import communication.ProtocolProtos;
import communication.ProtocolProtos.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;

public class Server {
    private TaskList taskList;
    int port;

    public Server(int port) throws IOException {
        this.port = port;
        taskList = new TaskList();
        startListening();
    }

    public Server() {
        taskList = new TaskList();
    }

    // TODO: for debug reasons here new Thread. For release should be removed
    private void startListening() throws IOException {
        new Thread(() -> {
            try (ServerSocket socket = new ServerSocket(port)) {
                System.out.println("Server started");
                Socket clientSocket = socket.accept();
                processClient(clientSocket);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void processClient(Socket clientSocket) {
        Thread clientThread = new Thread(() -> {
            System.out.println("Server: got client");
            try (InputStream inputStream = clientSocket.getInputStream()) {
                while (true) {
                    new Thread(() -> {
                        try {
                            processMessage(WrapperMessage.parseFrom(inputStream), clientSocket);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }).start();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        clientThread.start();
    }

    private void processMessage(WrapperMessage msg, Socket socket) {
        ServerRequest request = msg.getRequest();
        if (request.hasSubmit()) {
                processSubmitTaskMessage(request, socket);
        }

        if (request.hasSubscribe()) {
            processSubscribeOnTaskResultMessage(request.getSubscribe());
        }

        if (request.hasList()) {
            processGetTaskListMessage();
        }
    }

    private void processSubmitTaskMessage(ServerRequest request, Socket socket){
        SubmitTask submitTask = request.getSubmit();
        Task.Type type = getTaskType(submitTask);
        long a = getTaskParamValue(submitTask.getTask().getA());
        long b = getTaskParamValue(submitTask.getTask().getB());
        long p = getTaskParamValue(submitTask.getTask().getP());
        long m = getTaskParamValue(submitTask.getTask().getM());
        long n = submitTask.getTask().getN();
        // TODO: fix type
        int taskId = (int)submitTask(type, a, b, p, m, n);
        sendSubmitTaskResponse(socket, taskId, request.getRequestId());
    }

    private void sendSubmitTaskResponse(Socket socket, int taskId, long requestId) {
        WrapperMessage msg = WrapperMessage.newBuilder()
                .setResponse(ServerResponse
                        .newBuilder()
                        .setRequestId(requestId)
                        .setSubmitResponse(SubmitTaskResponse
                                .newBuilder()
                                .setSubmittedTaskId(taskId))).build();
        synchronized (socket) {
            try {
                msg.writeTo(socket.getOutputStream());
            } catch (IOException e) {
                System.err.println("Error writing submit task response to request " + requestId);
            }
        }
    }

    // TODO:
    private void processSubscribeOnTaskResultMessage(Subscribe subscribe) {
    }

    // TODO:
    private void processGetTaskListMessage() {
        List<Task> tasks = getTasksList();
    }


    long getTaskParamValue(ProtocolProtos.Task.Param param) {
        if (param.hasValue()) {
            return param.getValue();
        }

        if (param.hasDependentTaskId()) {
            return param.getDependentTaskId();
        }
        throw new IllegalArgumentException("Param has unset value");
    }

    Task.Type getTaskType(SubmitTask submitTask) {
        if (submitTask.getTask().getA().getParamValueCase().getNumber() == 0) {
            throw new IllegalArgumentException("Task type unset");
        }

        if (submitTask.getTask().getA().getParamValueCase().getNumber() == 1) {
            return Task.Type.INDEPENDENT;
        } else {
            return Task.Type.DEPENDENT;
        }
    }

    // Interface for manual testing
    public long submitTask(Task.Type type, long a, long b, long p, long m, long n) {
        return taskList.addTask(type, a, b, p, m, n);
    }

    public long subscribeOnTaskResult(long taskId) {
        return taskList.subscribeOnTaskResult(taskId);
    }

    public List<Task> getTasksList() {
        return taskList.getTasksList();
    }
}