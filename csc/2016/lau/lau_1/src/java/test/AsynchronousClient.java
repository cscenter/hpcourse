package test;

import communication.ProtocolProtos;
import communication.ProtocolProtos.*;
import server.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.List;

public class AsynchronousClient {
    private String id = "AClientID";
    private long currentRequestId;

    public void runTests(String addr, int port) {
        try (Socket s = new Socket(addr, port)) {
            System.out.println("Client: connected");
            // Here test routines
            // ----------------
            Thread responseProcessingThread = new Thread(() -> {
                while (true) {
                    try {
                        processResponse(s.getInputStream());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
            responseProcessingThread.start();

            sendSubmitIndependentTaskRequest(s, 3, 1, 4, 21, 1000); //8
            sendSubmitIndependentTaskRequest(s, 2, 5, 7, 21, 1000); //5
            sendSubmitIndependentTaskRequest(s, 3, 4, 3, 15, 1000); //7
            sendSubmitIndependentTaskRequest(s, 1, 3, 5, 15, 1000); //3
            sendSubmitDependentTaskRequest(s, 0, 1, 2, 3, 1000); // 2
            sendSubscribeRequest(s, 1);
            try {
                Thread.currentThread().sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            sendTaskListRequest(s);
            System.out.println("Client: all tasks finished");
            try {
                responseProcessingThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            // ----------------
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendSubscribeRequest(Socket socket, int taskId) throws IOException {
        OutputStream outputStream = socket.getOutputStream();
        WrapperMessage requestMsg = WrapperMessage.newBuilder().setRequest(ServerRequest.newBuilder()
                .setClientId(id)
                .setRequestId(getCurrentRequestId())
                .setSubscribe(Subscribe.newBuilder().setTaskId(taskId))).build();

        System.out.println("Client: sending subscribe msg");
        requestMsg.writeDelimitedTo(outputStream);
        System.out.println("Client: subscribe msg sent");
    }

    private void sendSubmitIndependentTaskRequest(Socket socket, long a, long b, long p, long m, long n) throws IOException {
        System.out.println("Client: sending fully independent task request");
        sendSubmitTaskRequest(socket,
                new TaskParam(TaskParam.Type.VALUE, a),
                new TaskParam(TaskParam.Type.VALUE, b),
                new TaskParam(TaskParam.Type.VALUE, p),
                new TaskParam(TaskParam.Type.VALUE, m),
                n);
        System.out.println("Client: fully independent task request sent");
    }

    private void sendSubmitDependentTaskRequest(Socket socket, int a, int b, int p, int m, int n) throws IOException {
        System.out.println("Client: sending fully dependent task request");
        sendSubmitTaskRequest(socket,
                new TaskParam(TaskParam.Type.TASK_ID, a),
                new TaskParam(TaskParam.Type.TASK_ID, b),
                new TaskParam(TaskParam.Type.TASK_ID, p),
                new TaskParam(TaskParam.Type.TASK_ID, m),
                n);
        System.out.println("Client: fully dependent task request sent");
    }

    private void sendSubmitTaskRequest(Socket socket, TaskParam a, TaskParam b, TaskParam p, TaskParam m, long n) throws IOException {
        System.out.println("Client: sending dependent task request");
        WrapperMessage msg = WrapperMessage.newBuilder().setRequest(
                ServerRequest.newBuilder().setSubmit(
                        SubmitTask.newBuilder().setTask(
                                ProtocolProtos.Task.newBuilder()
                                        .setA(getParamBuilder(a))
                                        .setB(getParamBuilder(b))
                                        .setP(getParamBuilder(p))
                                        .setM(getParamBuilder(m))
                                        .setN(n)))
                        .setClientId(id)
                        .setRequestId(getCurrentRequestId())
        ).build();
        msg.writeDelimitedTo(socket.getOutputStream());
        System.out.println("Client: dependent task request sent");
    }

    private ProtocolProtos.Task.Param.Builder getParamBuilder(TaskParam param) {
        if (param.getType() == TaskParam.Type.VALUE) {
            return ProtocolProtos.Task.Param.newBuilder().setValue(param.getValue());
        } else if (param.getType() == TaskParam.Type.TASK_ID){
            return ProtocolProtos.Task.Param.newBuilder().setDependentTaskId(param.getDependentTaskId());
        } else {
            throw new IllegalArgumentException("Malformed task param " + param.toString());
        }
    }

    private void sendTaskListRequest(Socket socket) throws IOException {
        WrapperMessage requestMsg = WrapperMessage.newBuilder().setRequest(ServerRequest.newBuilder()
                .setClientId(id)
                .setRequestId(getCurrentRequestId())
                .setList(ListTasks.newBuilder())).build();
        System.out.println("Client: sending task list request");
        requestMsg.writeDelimitedTo(socket.getOutputStream());
        System.out.println("Client: task list request SENT");
    }

    private void processResponse(InputStream inputStream) {
        try {
            WrapperMessage msg = WrapperMessage.parseDelimitedFrom(inputStream);
            if (!msg.hasResponse()) {
                throw new IllegalArgumentException("Client: received message is not response");
            }
            processResponseMessage(msg);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void processResponseMessage(WrapperMessage responseMsg) {
        if (responseMsg.getResponse().hasSubmitResponse()) {
            processSubmitResponse(responseMsg);
        } else if (responseMsg.getResponse().hasSubscribeResponse()) {
            processSubscribeResponse(responseMsg);
        } else if (responseMsg.getResponse().hasListResponse()) {
            processTaskListResponse(responseMsg);
        } else {
            throw new IllegalArgumentException("Client: malformed response");
        }
    }

    private void processSubscribeResponse(WrapperMessage responseMsg) {
        System.out.println("Client: GET subscribe response"
                + " request id: " + responseMsg.getResponse().getRequestId()
                + " result: " + responseMsg.getResponse().getSubscribeResponse().getValue());
    }

    private void processTaskListResponse(WrapperMessage responseMsg) {
        List<ListTasksResponse.TaskDescription> tasks = responseMsg.getResponse().getListResponse().getTasksList();
        System.out.println("Client: GET task list response");
        for (ListTasksResponse.TaskDescription task : tasks) {
            System.out.print("  Task id: " + task.getTaskId() + " params: ");
            printParam(task.getTask().getA());
            printParam(task.getTask().getB());
            printParam(task.getTask().getP());
            printParam(task.getTask().getM());
            System.out.println(" n = " + task.getTask().getN()
                    + (task.hasResult() ? " res: " + task.getResult() : " RUNNING"));
        }
        System.out.println("Client: end of tasks list");
    }

    private void printParam(communication.ProtocolProtos.Task.Param param) {
        if (param.hasValue()) {
            System.out.print("VALUE: " + param.getValue() + " ");
        } else if (param.hasDependentTaskId()) {
            System.out.print("TASK_ID: " + param.getDependentTaskId() + " ");
        } else {
            throw new IllegalStateException("Malformed task parameter");
        }
    }

    private void processSubmitResponse(WrapperMessage responseMsg) {
        System.out.println("Client: GET submit independent task response "
                + "request id: " + responseMsg.getResponse().getRequestId()
                + " task id: " + responseMsg.getResponse().getSubmitResponse().getSubmittedTaskId());
    }

    private long getCurrentRequestId() {
        return currentRequestId++;
    }
}
