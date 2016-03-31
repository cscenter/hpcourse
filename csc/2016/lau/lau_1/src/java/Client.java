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
                    sendSubmitIndependentTaskRequest(s, 3, 1, 4, 21, 1000);
                    sendSubscribeRequest(s, 0);
                    System.out.println("Client: all tasks finished");
                    //while (true);
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

        WrapperMessage responseMsg = WrapperMessage.parseDelimitedFrom(inputStream);
        System.out.println("Client: get subscribe response"
                + " request id: " + responseMsg.getResponse().getRequestId()
                + " result: " + responseMsg.getResponse().getSubscribeResponse().getValue());
    }

    void sendSubmitIndependentTaskRequest(Socket socket, long a, long b, long p, long m, long n) throws IOException {
        OutputStream outputStream = socket.getOutputStream();
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

        InputStream inputStream = socket.getInputStream();
        WrapperMessage responseMsg = WrapperMessage.parseDelimitedFrom(inputStream);
        System.out.println("Client: get submit task response "
                + "request id: " + responseMsg.getResponse().getRequestId()
                + " task id: " + responseMsg.getResponse().getSubmitResponse().getSubmittedTaskId());
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
