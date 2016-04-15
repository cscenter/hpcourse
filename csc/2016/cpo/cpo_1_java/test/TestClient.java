import communication.Protocol;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by Pavel Chursin on 15.04.2016.
 */
public class TestClient extends Thread {

    Socket s;
    String clientID;
    AtomicInteger reqID;
    ArrayList<Protocol.WrapperMessage> tasks = new ArrayList<>();

    public TestClient(String id, AtomicInteger reqID) throws IOException {
        s = new Socket("localhost", TaskServer.PORT);
        clientID = id;
        this.reqID = reqID;
    }

    public void addTask(Protocol.WrapperMessage wm) {
        tasks.add(wm);
    }

    private void sendToServer(Protocol.WrapperMessage msg) throws IOException {
        OutputStream os = s.getOutputStream();
        msg.writeDelimitedTo(s.getOutputStream());
        os.flush();
    }

    private void subscribeTo(int taskID) throws IOException {
        Protocol.Subscribe s = Protocol.Subscribe.newBuilder()
                .setTaskId(taskID)
                .build();
        Protocol.ServerRequest sr = Protocol.ServerRequest.newBuilder()
                .setRequestId(reqID.getAndIncrement())
                .setSubscribe(s)
                .setClientId(clientID)
                .build();
        sendToServer(Protocol.WrapperMessage.newBuilder().setRequest(sr).build());
    }

    public void run() {
        for (int i = 0; i < tasks.size(); i++) {
            Protocol.WrapperMessage wm = tasks.get(i);
            try {
                sendToServer(wm);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        for (int i = 0; i < tasks.size(); i++) {
            try {
                Protocol.WrapperMessage wm = Protocol.WrapperMessage.parseDelimitedFrom(s.getInputStream());
                int taskID = wm.getResponse().getSubmitResponse().getSubmittedTaskId();
                subscribeTo(taskID);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        for (int i = 0; i < tasks.size(); i++) {
            try {
                Protocol.WrapperMessage wm = Protocol.WrapperMessage.parseDelimitedFrom(s.getInputStream());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        System.out.println("Client: " + clientID + " shuts down");
    }
}
