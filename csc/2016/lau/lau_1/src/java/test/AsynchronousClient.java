package test;

import communication.ProtocolProtos;
import communication.ProtocolProtos.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.List;

public class AsynchronousClient {
    String id = "AsynchronousClientID";
    private long currentRequestId;

    // TODO: may be redundant start in new thread
    public void runTests(String addr, int port) {
        new Thread(() -> {
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
        }).start();
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
        System.out.println("Client: sending independent task request");
        WrapperMessage msg = WrapperMessage.newBuilder().setRequest(
                ServerRequest.newBuilder().setSubmit(
                        SubmitTask.newBuilder().setTask(
                                ProtocolProtos.Task.newBuilder()
                                        .setA(ProtocolProtos.Task.Param.newBuilder().setValue(a))
                                        .setB(ProtocolProtos.Task.Param.newBuilder().setValue(b))
                                        .setP(ProtocolProtos.Task.Param.newBuilder().setValue(p))
                                        .setM(ProtocolProtos.Task.Param.newBuilder().setValue(m))
                                        .setN(n)))
                        .setClientId(id)
                        .setRequestId(getCurrentRequestId())
        ).build();
        msg.writeDelimitedTo(socket.getOutputStream());
        System.out.println("Client: independent task request sent");
    }

    private void sendSubmitDependentTaskRequest(Socket socket, int a, int b, int p, int m, int n) throws IOException {
        System.out.println("Client: sending dependent task request");
        WrapperMessage msg = WrapperMessage.newBuilder().setRequest(
                ServerRequest.newBuilder().setSubmit(
                        SubmitTask.newBuilder().setTask(
                                ProtocolProtos.Task.newBuilder()
                                        .setA(ProtocolProtos.Task.Param.newBuilder().setDependentTaskId(a))
                                        .setB(ProtocolProtos.Task.Param.newBuilder().setDependentTaskId(b))
                                        .setP(ProtocolProtos.Task.Param.newBuilder().setDependentTaskId(p))
                                        .setM(ProtocolProtos.Task.Param.newBuilder().setDependentTaskId(m))
                                        .setN(n)))
                        .setClientId(id)
                        .setRequestId(getCurrentRequestId())
        ).build();
        msg.writeDelimitedTo(socket.getOutputStream());
        System.out.println("Client: dependent task request sent");
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
            if (task.getTask().getA().hasValue()) {
                System.out.print("  Independent task id: " + task.getTaskId() + " params: a = " + task.getTask().getA().getValue()
                        + " b = " + task.getTask().getB().getValue() + " p = " + task.getTask().getP().getValue()
                        + " m = " + task.getTask().getM().getValue());
            } else {
                System.out.print("  Dependent task id: " + task.getTaskId() + " params: a = " + task.getTask().getA().getDependentTaskId()
                        + " b = " + task.getTask().getB().getDependentTaskId() + " p = " + task.getTask().getP().getDependentTaskId()
                        + " m = " + task.getTask().getM().getDependentTaskId());
            }
            System.out.println(" n = " + task.getTask().getN()
                    + (task.hasResult() ? " res: " + task.getResult() : " RUNNING"));
        }
        System.out.println("Client: end of tasks list");
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
