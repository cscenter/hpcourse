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
        new Thread(() -> {
            System.out.println("Server: got client");
            try (InputStream inputStream = clientSocket.getInputStream()) {
                while (true) {
                    processMessage(WrapperMessage.parseFrom(inputStream));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void processMessage(WrapperMessage msg) {
        ServerRequest request = msg.getRequest();
        if (request.hasSubmit()) {
            submitTask(request.getSubmit());
        }

        if (request.hasSubscribe()) {
            subscribeOnTaskResult(request.getSubscribe());
        }

        if (request.hasList()) {
            getTaskList(request.getList());
        }
    }

    private void submitTask(SubmitTask submitTask) {
        Task.Type type = getTaskType(submitTask);
        long a = getTaskParamValue(submitTask.getTask().getA());
        long b = getTaskParamValue(submitTask.getTask().getB());
        long p = getTaskParamValue(submitTask.getTask().getP());
        long m = getTaskParamValue(submitTask.getTask().getM());
        long n = submitTask.getTask().getN();
        submitTask(type, a, b, p, m, n);
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

    // TODO:
    private void subscribeOnTaskResult(Subscribe subscribe) {

    }

    // TODO:
    private void getTaskList(ListTasks listTasks) {

    }

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