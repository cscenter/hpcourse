import communication.ProtocolProtos;
import communication.ProtocolProtos.*;
import server.Task;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

public class Client {
    OutputStream outputStream;
    String id = "ClientID";
    long currentRequestId = 0;

    void sendSubscribeRequest(String addr, int port) {
        try (Socket s = new Socket(addr, port)) {
            System.out.println("Client: connected");
            outputStream = s.getOutputStream();
            sendSubscribeRequest(outputStream);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void sendSubscribeRequest(OutputStream outputStream) throws IOException {
        WrapperMessage msg = buildSubscribeRequest(1);
        System.out.println("Client: writing msg");
        msg.writeTo(outputStream);
    }

    private WrapperMessage buildSubscribeRequest(int taskId) {
        WrapperMessage.Builder msg = WrapperMessage.newBuilder();
        msg.setRequest(ServerRequest.newBuilder()
                .setClientId(id)
                .setRequestId(currentRequestId++)
                .setSubscribe(Subscribe.newBuilder().setTaskId(taskId)));
        return msg.build();
    }

    // TODO:
    void sendSubmitIndependentTaskRequest(OutputStream outputStream, long a, long b, long p, long m, long n) throws IOException {
        System.out.println("Sending independent task request");
        WrapperMessage msg;
        msg = WrapperMessage.newBuilder().setRequest(
                ServerRequest.newBuilder().setSubmit(
                        SubmitTask.newBuilder().setTask(
                                ProtocolProtos.Task.newBuilder()
                                        .setA(ProtocolProtos.Task.Param.newBuilder().setValue(a))
                                        .setB(ProtocolProtos.Task.Param.newBuilder().setValue(b))
                                        .setP(ProtocolProtos.Task.Param.newBuilder().setValue(p))
                                        .setM(ProtocolProtos.Task.Param.newBuilder().setValue(m))
                                        .setN(n)
                        ))).build();
        msg.writeTo(outputStream);
    }

    void sendSubmitDependentTaskRequest(OutputStream outputStream, int a, int b, int p, int m, int n) throws IOException {
        System.out.println("Sending dependent task request");
        WrapperMessage msg;
        msg = WrapperMessage.newBuilder().setRequest(
                ServerRequest.newBuilder().setSubmit(
                        SubmitTask.newBuilder().setTask(
                                ProtocolProtos.Task.newBuilder()
                                        .setA(ProtocolProtos.Task.Param.newBuilder().setDependentTaskId(a))
                                        .setB(ProtocolProtos.Task.Param.newBuilder().setDependentTaskId(b))
                                        .setP(ProtocolProtos.Task.Param.newBuilder().setDependentTaskId(p))
                                        .setM(ProtocolProtos.Task.Param.newBuilder().setDependentTaskId(m))
                                        .setN(n)
                        ))).build();
        msg.writeTo(outputStream);
    }

    void sendTaskListRequest() {

    }
}
