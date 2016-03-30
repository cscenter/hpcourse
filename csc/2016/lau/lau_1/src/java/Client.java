import communication.ProtocolProtos;
import communication.ProtocolProtos.*;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

public class Client {
    OutputStream outputStream;
    String id = "ClientID";
    private long currentRequestId;

    void runTests(String addr, int port) {
        try (Socket s = new Socket(addr, port)) {
            System.out.println("Client: connected");
            outputStream = s.getOutputStream();
            // Here test routines
            // ----------------
            sendSubmitIndependentTaskRequest(outputStream, 1, 2, 3, 4, 1000);
            sendSubscribeRequest(outputStream, 0);
            // ----------------
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendSubscribeRequest(OutputStream outputStream, int taskId) throws IOException {
        WrapperMessage msg = WrapperMessage.newBuilder().setRequest(ServerRequest.newBuilder()
                .setClientId(id)
                .setRequestId(getCurrentRequestId())
                .setSubscribe(Subscribe.newBuilder().setTaskId(taskId))).build();

        System.out.println("Client: sending subscribe msg");
        msg.writeTo(outputStream);
    }

    void sendSubmitIndependentTaskRequest(OutputStream outputStream, long a, long b, long p, long m, long n) throws IOException {
        System.out.println("Sending independent task request");
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
        msg.writeTo(outputStream);
    }

    void sendSubmitDependentTaskRequest(OutputStream outputStream, int a, int b, int p, int m, int n) throws IOException {
        System.out.println("Sending independent task request");
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
        msg.writeTo(outputStream);
    }

    void sendTaskListRequest() throws IOException {
        WrapperMessage msg = WrapperMessage.newBuilder().setRequest(ServerRequest.newBuilder()
                .setClientId(id)
                .setRequestId(getCurrentRequestId())
                .setList(ListTasks.newBuilder())).build();
        msg.writeTo(outputStream);
    }

    public long getCurrentRequestId() {
        return currentRequestId++;
    }
}
