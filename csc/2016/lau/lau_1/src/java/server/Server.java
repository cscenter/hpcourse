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
    }

    public Server() {
        taskList = new TaskList();
    }

    public void startListening() throws IOException {
        try (ServerSocket socket = new ServerSocket(port)) {
            System.out.println("Server started");
            Socket clientSocket = socket.accept();
            processClient(clientSocket);
        } catch (IOException e) {
            e.printStackTrace();
        }
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
        TaskParam a = createParam(submitTask.getTask().getA());
        TaskParam b = createParam(submitTask.getTask().getB());
        TaskParam p = createParam(submitTask.getTask().getP());
        TaskParam m = createParam(submitTask.getTask().getM());

        long n = submitTask.getTask().getN();
        int taskId = submitTask(request.getClientId(), a, b, p, m, n);
        sendSubmitTaskResponse(socket, taskId, request.getRequestId());
    }

    private TaskParam createParam(ProtocolProtos.Task.Param param) {
        return new TaskParam(getParamType(param), getTaskParamValue(param));
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
            ProtocolProtos.Task.Builder taskBuilder =
                    ProtocolProtos.Task.newBuilder()
                    .setA(getParamBuilder(x.params[0]))
                    .setB(getParamBuilder(x.params[1]))
                    .setP(getParamBuilder(x.params[2]))
                    .setM(getParamBuilder(x.params[3]))
                    .setN(x.n);

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

    private ProtocolProtos.Task.Param.Builder getParamBuilder(TaskParam param) {
        if (param.type == TaskParam.Type.VALUE) {
            return ProtocolProtos.Task.Param.newBuilder().setValue(param.value);
        } else if (param.type == TaskParam.Type.TASK_ID) {
            return ProtocolProtos.Task.Param.newBuilder().setDependentTaskId(param.dependentTaskId);
        } else {
            throw new IllegalArgumentException("Illegal param " + param.toString());
        }
    }

    private long getTaskParamValue(ProtocolProtos.Task.Param param) {
        if (param.hasValue()) {
            return param.getValue();
        }

        if (param.hasDependentTaskId()) {
            return param.getDependentTaskId();
        }
        throw new IllegalArgumentException("Param has unset value");
    }

    private TaskParam.Type getParamType(ProtocolProtos.Task.Param param) {
        if (param.getParamValueCase().getNumber() == 0) {
            throw new IllegalArgumentException("Task type unset");
        }

        if (param.getParamValueCase().getNumber() == 1) {
            return TaskParam.Type.VALUE;
        } else {
            return TaskParam.Type.TASK_ID;
        }
    }

    public int submitTask(String clientId, TaskParam a, TaskParam b, TaskParam p, TaskParam m, long n) {
        return taskList.addTask(clientId, a, b, p, m, n);
    }

    public long subscribeOnTaskResult(int taskId) {
        return taskList.subscribeOnTaskResult(taskId);
    }

    public List<Task> getTasksList() {
        return taskList.getTasksList();
    }
}