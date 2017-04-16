package client;

import communication.Common;
import communication.Protocol;

import java.io.*;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class Client{

    private String host;
    private int port;
    private int clientId;
    private static AtomicInteger requestCount = new AtomicInteger();
    private static AtomicInteger clientCount = new AtomicInteger();


    public Client(String host, int port) {
        this.host = host;
        this.port = port;
        this.clientId = clientCount.addAndGet(1);
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public int submitTask(Protocol.Task task) {
        Protocol.ServerRequest.Builder requestBuilder = Protocol.ServerRequest.newBuilder().
                setSubmit(Protocol.SubmitTask.newBuilder().setTask(task));
        Protocol.ServerResponse response = run(requestBuilder);

        return response.getSubmitResponse().getSubmittedTaskId();
    }

    public Long subscribe(int taskId) {
        Protocol.ServerRequest.Builder requestBuilder = Protocol.ServerRequest.newBuilder().
                setSubscribe(Protocol.Subscribe.newBuilder().setTaskId(taskId));

        Protocol.ServerResponse response = run(requestBuilder);
        Protocol.SubscribeResponse subscribeResponse = response.getSubscribeResponse();

        if (subscribeResponse.getStatus().equals(Protocol.Status.OK)) {
            return subscribeResponse.getValue();
        }

        return null;
    }

    public List<Protocol.ListTasksResponse.TaskDescription> getList() {
        Protocol.ServerRequest.Builder requestBuilder = Protocol.ServerRequest.newBuilder().
                setList(Protocol.ListTasks.newBuilder());
        Protocol.ServerResponse response = run(requestBuilder);
        return response.getListResponse().getTasksList();
    }

    public Protocol.ServerResponse run(Protocol.ServerRequest.Builder requestBuilder) {
        try (   Socket clientSocket = new Socket(this.host, this.port);
                InputStream in = clientSocket.getInputStream();
                OutputStream out = clientSocket.getOutputStream()
                ) {

            requestBuilder.setClientId(String.valueOf(clientId));
            requestBuilder.setRequestId(requestCount.addAndGet(1));

            Protocol.WrapperMessage requestMessage = Protocol.WrapperMessage.newBuilder().setRequest(requestBuilder).build();

            requestMessage.writeDelimitedTo(out);
            out.flush();

            Protocol.WrapperMessage responseMessage = Protocol.WrapperMessage.parseFrom(in);
            return responseMessage.getResponse();
        } catch (IOException e) {
            System.out.println("Error create client at port: " + this.port);
            e.printStackTrace();
        }
        return null;
    }
}
