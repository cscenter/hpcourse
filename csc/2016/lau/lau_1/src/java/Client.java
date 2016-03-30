import communication.ProtocolProtos;

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
            ProtocolProtos.WrapperMessage msg = buildSubscribeRequest(1);
            System.out.println("Client: writing msg");
            msg.writeTo(outputStream);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void sendTaskRequest() {

    }

    void sendTaskListRequest() {

    }

    private ProtocolProtos.WrapperMessage buildSubscribeRequest(int taskId) {
        ProtocolProtos.WrapperMessage.Builder msg = ProtocolProtos.WrapperMessage.newBuilder();
        msg.setRequest(ProtocolProtos.ServerRequest.newBuilder()
                .setClientId(id)
                .setRequestId(currentRequestId++)
                .setSubscribe(ProtocolProtos.Subscribe.newBuilder().setTaskId(taskId)));
        return msg.build();
    }
}
