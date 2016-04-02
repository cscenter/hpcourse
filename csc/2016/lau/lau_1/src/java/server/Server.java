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
    private int port;

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
            Thread.currentThread().setName("ServerThread");
            try (InputStream inputStream = clientSocket.getInputStream()) {
                while (true) {
                    WrapperMessage msg = WrapperMessage.parseDelimitedFrom(inputStream);
                    if (msg != null) {
                        new Thread(() -> {
                            processMessage(msg, clientSocket);
                        }).start();
                    }
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
        } else
        if (request.hasSubscribe()) {
            processSubscribeOnTaskResultMessage(request, socket);
        } else
        if (request.hasList()) {
            processGetTaskListMessage(request, socket);
        } else {
            throw new IllegalArgumentException("Server: Malformed request");
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
        int taskId = submitTask(type, request.getClientId(), a, b, p, m, n);
        sendSubmitTaskResponse(socket, taskId, request.getRequestId());
    }

    private void sendSubmitTaskResponse(Socket socket, int taskId, long requestId) {
        WrapperMessage msg = WrapperMessage.newBuilder()
                .setResponse(ServerResponse
                        .newBuilder()
                        .setRequestId(requestId)
                        .setSubmitResponse(SubmitTaskResponse
                                .newBuilder()
                                .setStatus(Status.OK)
                                .setSubmittedTaskId(taskId))).build();
        synchronized (socket) {
            try {
                msg.writeDelimitedTo(socket.getOutputStream());
            } catch (IOException e) {
                System.err.println("Error writing submit task response to request " + requestId);
                e.printStackTrace();
            }
        }
    }

    private void processSubscribeOnTaskResultMessage(ServerRequest request, Socket socket) {
        long value = subscribeOnTaskResult(request.getSubscribe().getTaskId());
        long requestId = request.getRequestId();
        WrapperMessage msg = WrapperMessage.newBuilder()
                .setResponse(
                        ServerResponse.newBuilder()
                        .setRequestId(requestId)
                        .setSubscribeResponse(
                                SubscribeResponse.newBuilder()
                                        .setStatus(Status.OK)
                                        .setValue(value))).build();
        synchronized (socket) {
            try {
                msg.writeDelimitedTo(socket.getOutputStream());
            } catch (IOException e) {
                System.err.println("Could not write subscribe response on request id " + requestId);
                e.printStackTrace();
            }
        }
    }

    private void processGetTaskListMessage(ServerRequest request, Socket socket) {
        List<Task> tasks = getTasksList();
        long requestId = request.getRequestId();
        ListTasksResponse.Builder listTasksResponse = ListTasksResponse.newBuilder().setStatus(Status.OK);
        for (Task x : tasks) {
            ProtocolProtos.Task.Builder taskBuilder;
            if (x.type == Task.Type.INDEPENDENT) {
                taskBuilder = ProtocolProtos.Task.newBuilder()
                        .setA(ProtocolProtos.Task.Param.newBuilder().setValue(x.a))
                        .setB(ProtocolProtos.Task.Param.newBuilder().setValue(x.b))
                        .setP(ProtocolProtos.Task.Param.newBuilder().setValue(x.p))
                        .setM(ProtocolProtos.Task.Param.newBuilder().setValue(x.m))
                        .setN(x.n);
            } else {
                taskBuilder = ProtocolProtos.Task.newBuilder()
                        .setA(ProtocolProtos.Task.Param.newBuilder().setDependentTaskId((int)x.a))
                        .setB(ProtocolProtos.Task.Param.newBuilder().setDependentTaskId((int)x.b))
                        .setP(ProtocolProtos.Task.Param.newBuilder().setDependentTaskId((int)x.p))
                        .setM(ProtocolProtos.Task.Param.newBuilder().setDependentTaskId((int)x.m))
                        .setN(x.n);
            }
            ListTasksResponse.TaskDescription.Builder taskDescriptionBuilder
                    = ListTasksResponse.TaskDescription.newBuilder()
                    .setTaskId(x.id)
                    .setClientId(x.clientId)
                    .setTask(taskBuilder);
            if (x.status == Task.Status.FINISHED) {
                taskDescriptionBuilder.setResult(x.result);
            }

            listTasksResponse.addTasks(taskDescriptionBuilder);
        }
        WrapperMessage msg = WrapperMessage.newBuilder().setResponse(
                ServerResponse.newBuilder()
                        .setRequestId(requestId)
                        .setListResponse(listTasksResponse)).build();
        synchronized (socket) {
            try {
                msg.writeDelimitedTo(socket.getOutputStream());
            } catch (IOException e) {
                System.err.println("Can not write task list response");
                e.printStackTrace();
            }
        }
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

    public int submitTask(Task.Type type, String clientId, long a, long b, long p, long m, long n) {
        return taskList.addTask(type, clientId, a, b, p, m, n);
    }

    public long subscribeOnTaskResult(long taskId) {
        return taskList.subscribeOnTaskResult(taskId);
    }

    public List<Task> getTasksList() {
        return taskList.getTasksList();
    }
}