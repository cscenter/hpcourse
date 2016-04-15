package client;

import protocol.Protocol;
import task.Param;
import task.Task;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.Socket;

public class Client {
    private final Socket socket;
    private long requestId;
    private Thread listenThread;

    public Client(String host, int port) throws IOException {
        socket = new Socket(host, port);
        requestId = 0;
        listenThread = new ListenClientThread(socket);
        listenThread.start();
    }


    private void sendRequest(Protocol.ServerRequest serverRequest) {
        try (OutputStream os = socket.getOutputStream()) {
            synchronized (os) {
                os.write(serverRequest.getSerializedSize());
                serverRequest.writeTo(os);
                os.flush();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void sendSubmitTaskRequest(long a, long b, long p, long m, long n) {
        Param ap = Param.newParamWithValue(a);
        Param bp = Param.newParamWithValue(b);
        Param pp = Param.newParamWithValue(p);
        Param mp = Param.newParamWithValue(m);
        Param np = Param.newParamWithValue(n);
        Task task = Task.newTask(0, ap, bp, pp, mp, np);

        Protocol.SubmitTask.Builder submitTask = Protocol.SubmitTask.newBuilder();
        submitTask.setTask(task.toProtocolTask());

        Protocol.ServerRequest.Builder serverRequest = Protocol.ServerRequest.newBuilder();
        serverRequest.setSubmit(submitTask.build());
        serverRequest.setRequestId(requestId++);

        sendRequest(serverRequest.build());
    }

    public void sendSubscribeTaskRequest(long taskId) {
        Protocol.SubscribeTask.Builder subscribeTask = Protocol.SubscribeTask.newBuilder();
        subscribeTask.setTaskId(taskId);

        Protocol.ServerRequest.Builder serverRequest = Protocol.ServerRequest.newBuilder();
        serverRequest.setSubscribe(subscribeTask.build());
        serverRequest.setRequestId(requestId++);

        sendRequest(serverRequest.build());
    }

    public void sendListTaskRequest() {
        Protocol.ListTask.Builder listTask = Protocol.ListTask.newBuilder();

        Protocol.ServerRequest.Builder serverRequest = Protocol.ServerRequest.newBuilder();
        serverRequest.setList(listTask.build());
        serverRequest.setRequestId(requestId++);

        sendRequest(serverRequest.build());
    }

    public void stop() throws IOException {
        try {
            listenThread.interrupt();
            socket.close();
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }
}
