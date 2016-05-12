import communication.Parameter;
import communication.Protocol;
import communication.Task;
import server.Server;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;


public class TrainClient extends Thread {
    private static final String CLIENT_ID = "CLIENT";
    private Socket clientSocket;
    private AtomicInteger requestId;

    public TrainClient() throws IOException {
        clientSocket = new Socket("localhost", Server.PORT);
        this.requestId = new AtomicInteger(0);
    }

    @Override
    public void run() {
        try {
            while (true) {
                int size = clientSocket.getInputStream().read();
                if (size <= 0) return;
                byte buf[] = new byte[size];
                clientSocket.getInputStream().read(buf);
                Protocol.ServerResponse response = Protocol.ServerResponse.parseFrom(buf);
                System.out.println(response);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void sendTask(Parameter a, Parameter b, Parameter p, Parameter m, long n) {
        Task task = new Task(a, b, p, m, n, 0, CLIENT_ID);

        Protocol.SubmitTask.Builder taskBuilder = Protocol.SubmitTask.newBuilder();
        taskBuilder.setTask(task.toProtocolTask());
        Protocol.ServerRequest.Builder builder = Protocol.ServerRequest.newBuilder();
        builder.setClientId(CLIENT_ID);
        builder.setRequestId(requestId.getAndIncrement());
        builder.setSubmit(taskBuilder.build());
        sendData(builder.build());
    }

    public void subscribeToTask(int id) {
        Protocol.Subscribe.Builder subscribeBuider = Protocol.Subscribe.newBuilder();
        subscribeBuider.setTaskId(id);
        Protocol.ServerRequest.Builder serverRequestBuilder = Protocol.ServerRequest.newBuilder();
        serverRequestBuilder.setSubscribe(subscribeBuider.build());
        serverRequestBuilder.setClientId(CLIENT_ID);
        serverRequestBuilder.setRequestId(requestId.getAndIncrement());
        sendData(serverRequestBuilder.build());
    }

    public void listAllTasks() {
        Protocol.ServerRequest.Builder builder = Protocol.ServerRequest.newBuilder();
        builder.setList(Protocol.ListTasks.newBuilder().build());
        builder.setClientId(CLIENT_ID);
        builder.setRequestId(requestId.getAndIncrement());
        sendData(builder.build());
    }

    private void sendData(Protocol.ServerRequest serverRequest) {
        try {
            OutputStream outputStream = clientSocket.getOutputStream();
            synchronized (outputStream) {
                outputStream.write(serverRequest.getSerializedSize());
                serverRequest.writeTo(outputStream);
                outputStream.flush();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
