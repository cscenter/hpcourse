import communication.ProtocolProtos;
import communication.ProtocolProtos.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class Client {
    OutputStream outputStream;
    String id = "ClientID";
    private long currentRequestId;

    // TODO: may be redundant start in new thread
    void runTests(String addr, int port) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try (Socket s = new Socket(addr, port)) {
                    System.out.println("Client: connected");
                    outputStream = s.getOutputStream();
                    // Here test routines
                    // ----------------
                    sendSubmitIndependentTaskRequest(outputStream, 3, 1, 4, 21, 1000);
                    sendSubscribeRequest(s, 0);
                    while (true);
                    // ----------------
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void sendSubscribeRequest(Socket socket, int taskId) throws IOException {
        OutputStream outputStream = socket.getOutputStream();
        InputStream inputStream = socket.getInputStream();
        WrapperMessage requestMsg = WrapperMessage.newBuilder().setRequest(ServerRequest.newBuilder()
                .setClientId(id)
                .setRequestId(getCurrentRequestId())
                .setSubscribe(Subscribe.newBuilder().setTaskId(taskId))).build();

        System.out.println("Client: sending subscribe msg");
        requestMsg.writeDelimitedTo(outputStream);
        System.out.println("Client: subscribe msg sent");

//        WrapperMessage responseMsg = WrapperMessage.parseDelimitedFrom(inputStream);
//        System.out.print("Client: get subscribe response "
//                + "request id: " + requestMsg.getResponse().getRequestId()
//                + " result: " + requestMsg.getResponse().getSubscribeResponse().getValue());
    }

    void sendSubmitIndependentTaskRequest(OutputStream outputStream, long a, long b, long p, long m, long n) throws IOException {
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
        msg.writeDelimitedTo(outputStream);
        System.out.println("Client: independent task request sent");
    }

    void sendSubmitDependentTaskRequest(OutputStream outputStream, int a, int b, int p, int m, int n) throws IOException {
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
        msg.writeDelimitedTo(outputStream);
        System.out.println("Client: dependent task request sent");
    }

    void sendTaskListRequest() throws IOException {
        WrapperMessage msg = WrapperMessage.newBuilder().setRequest(ServerRequest.newBuilder()
                .setClientId(id)
                .setRequestId(getCurrentRequestId())
                .setList(ListTasks.newBuilder())).build();
        msg.writeDelimitedTo(outputStream);
    }

    public long getCurrentRequestId() {
        return currentRequestId++;
    }
}
