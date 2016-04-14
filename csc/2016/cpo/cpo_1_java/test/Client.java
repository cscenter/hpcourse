import communication.Protocol;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import static communication.Protocol.*;

/**
 * Created by Pavel Chursin on 14.04.2016.
 */
public class Client {

    Socket s;
    String clientID;
    long reqID = 0;

    public Client(String id) throws IOException {
        clientID = id;
        s = new Socket("localhost", TaskServer.PORT);
        sendToServer(generateSubmitRequest());
        sendToServer(generateSubmitRequest());
        for (int i = 0; i < 2; i++) {
            ServerResponse resp = WrapperMessage.parseDelimitedFrom(s.getInputStream()).getResponse();
            System.out.println(resp.getRequestId());
            int taskID = resp.getSubmitResponse().getSubmittedTaskId();
            System.out.println(taskID);
            System.out.println(resp.getSubmitResponse().getStatus());
            subscribeTo(taskID);
        }

        for (int i = 0; i < 2; i++) {
            ServerResponse resp = WrapperMessage.parseDelimitedFrom(s.getInputStream()).getResponse();
            System.out.println("subscribe:");
            System.out.println(resp.getRequestId());
            System.out.println(resp.getSubscribeResponse().getValue());
        }

    }

    private void subscribeTo(int taskID) throws IOException {
        Subscribe s = Subscribe.newBuilder()
                .setTaskId(taskID)
                .build();
        ServerRequest sr = ServerRequest.newBuilder()
                .setRequestId(reqID++)
                .setSubscribe(s)
                .setClientId(clientID)
                .build();
        sendToServer(WrapperMessage.newBuilder().setRequest(sr).build());
    }

    private WrapperMessage generateSubmitRequest() {
        long a = (long) Math.abs(Math.random()*Long.MAX_VALUE);
        long b = (long) Math.abs(Math.random()*Long.MAX_VALUE);
        long p = (long) Math.abs(Math.random()*Long.MAX_VALUE);
        long m = (long) Math.abs(Math.random()*Long.MAX_VALUE);
        //long n = (long) Math.abs(Math.random()*Long.MAX_VALUE);
        long n = (long) Math.abs(Math.random()*500000000);

        Task t = Task.newBuilder()
                .setA(Task.Param.newBuilder().setValue(a).build())
                .setB(Task.Param.newBuilder().setValue(b).build())
                .setP(Task.Param.newBuilder().setValue(p).build())
                .setM(Task.Param.newBuilder().setValue(m).build())
                .setN(n)
                .build();
        SubmitTask st = SubmitTask.newBuilder()
                .setTask(t)
                .build();
        ServerRequest sr = ServerRequest.newBuilder()
                .setRequestId(reqID++)
                .setClientId(clientID)
                .setSubmit(st)
                .build();
        return WrapperMessage.newBuilder().setRequest(sr).build();
    }

    private void sendToServer(WrapperMessage msg) throws IOException {
        OutputStream os = s.getOutputStream();
        msg.writeDelimitedTo(s.getOutputStream());
        os.flush();
    }

    public static void main(String[] args) throws IOException {
        new Client("test_1");
    }
}
