package test;

import communication.ProtocolProtos;
import communication.ProtocolProtos.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.util.List;

public class SynchronousClient {
    String id = "SynchronousClientID";
    private long currentRequestId;

    public void runTests(String addr, int port) {
        try (Socket s = new Socket(addr, port)) {
            System.out.println("Client: connected");
            // Here test routines
            // ----------------
            int id1 = sendSubmitIndependentTaskRequest(s, 3, 1, 4, 21, 1000); //8
            int id2 = sendSubmitIndependentTaskRequest(s, 2, 5, 7, 21, 1000); //5
            int id3 = sendSubmitIndependentTaskRequest(s, 3, 4, 3, 15, 1000); //7
            int id4 = sendSubmitIndependentTaskRequest(s, 1, 3, 5, 15, 1000); //3
            int id5 = sendSubmitDependentTaskRequest(s, id1, id2, id3, id4, 1000); // 2
            sendSubscribeRequest(s, id1);
            sendSubscribeRequest(s, id2);
            sendSubscribeRequest(s, id3);
            sendSubscribeRequest(s, id4);
            sendSubscribeRequest(s, id5);
            int id6 = sendSubmitIndependentTaskRequest(s, 3, 1, 4, 21, 1000001); //19
            int id7 = sendSubmitIndependentTaskRequest(s, 2, 5, 9, 21, 1000001); //8
            int id8 = sendSubmitIndependentTaskRequest(s, 1, 8, 6, 31, 1000001); //9
            int id9 = sendSubmitIndependentTaskRequest(s, 7, 3, 5, 101, 1000001); //38
            int id10 = sendSubmitDependentTaskRequest(s, id6, id7, id8, id9, 10000001); //34
            sendTaskListRequest(s);
            sendSubscribeRequest(s, id7);
            sendTaskListRequest(s);
            Thread.currentThread().sleep(1000);
            sendTaskListRequest(s);
            System.out.println("Client: all tasks finished");
            // ----------------
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void sendSubscribeRequest(Socket socket, int taskId) throws IOException {
        WrapperMessage requestMsg = WrapperMessage.newBuilder().setRequest(ServerRequest.newBuilder()
                .setClientId(id)
                .setRequestId(getCurrentRequestId())
                .setSubscribe(Subscribe.newBuilder().setTaskId(taskId))).build();

        System.out.println("Client: sending subscribe msg");
        requestMsg.writeDelimitedTo(socket.getOutputStream());
        System.out.println("Client: subscribe msg sent");
        processSubscribeResponse(socket.getInputStream());
    }

    private void processSubscribeResponse(InputStream inputStream) throws IOException {
        WrapperMessage responseMsg = WrapperMessage.parseDelimitedFrom(inputStream);
        System.out.println("Client: GET subscribe response"
                + " request id: " + responseMsg.getResponse().getRequestId()
                + " result: " + responseMsg.getResponse().getSubscribeResponse().getValue());
    }

    private int sendSubmitIndependentTaskRequest(Socket socket, long a, long b, long p, long m, long n) throws IOException {
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
        return processSubmitResponse(socket.getInputStream());
    }

    private int sendSubmitDependentTaskRequest(Socket socket, int a, int b, int p, int m, int n) throws IOException {
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
        return processSubmitResponse(socket.getInputStream());
    }

    private int processSubmitResponse(InputStream inputStream) throws IOException {
        WrapperMessage responseMsg = WrapperMessage.parseDelimitedFrom(inputStream);
        System.out.println("Client: GET submit independent task response "
                + "request id: " + responseMsg.getResponse().getRequestId()
                + " task id: " + responseMsg.getResponse().getSubmitResponse().getSubmittedTaskId());
        return responseMsg.getResponse().getSubmitResponse().getSubmittedTaskId();
    }

    private void sendTaskListRequest(Socket socket) throws IOException {
        WrapperMessage requestMsg = WrapperMessage.newBuilder().setRequest(ServerRequest.newBuilder()
                .setClientId(id)
                .setRequestId(getCurrentRequestId())
                .setList(ListTasks.newBuilder())).build();
        System.out.println("Client: sending task list request");
        requestMsg.writeDelimitedTo(socket.getOutputStream());
        System.out.println("Client: task list request SENT");
        processTaskListResponse(socket.getInputStream());
    }

    private void processTaskListResponse(InputStream inputStream) throws IOException {
        WrapperMessage responseMsg = WrapperMessage.parseDelimitedFrom(inputStream);
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

    private long getCurrentRequestId() {
        return currentRequestId++;
    }
}
