package ru.compscicenter.hpc2016.ha1.client;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;

import ru.compscicenter.hpc2016.ha1.communication.Protocol;


public class Client extends Thread {
    private Socket clientSocket;
    private String clientId;
    private long requestId;

    public Client(String host, int port, String clientId) throws IOException {
        this.clientSocket = new Socket(host, port);
        this.clientId = clientId;
        this.requestId = 0;
        start();
    }

    @Override
    public void run() {
        try {
            while (true) {
                Protocol.ServerResponse response = getServerResponse();
                System.out.format("Client %s says:%n%s%n", clientId, response);
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

    Protocol.ServerResponse getServerResponse() throws IOException {
        InputStream is = clientSocket.getInputStream();
        return Protocol.ServerResponse.parseDelimitedFrom(is);
    }

    private void sendRequest(Protocol.ServerRequest serverRequest) throws IOException {
        synchronized (clientSocket) {
            serverRequest.writeDelimitedTo(clientSocket.getOutputStream());
        }
    }

    public void sendSubmitTaskRequest(long a, long b, long p, long m, long n) throws IOException {
        Protocol.Task task = buildProtocolTask(a, b, p, m, n);

        Protocol.SubmitTask.Builder taskBuilder = Protocol.SubmitTask.newBuilder();
        taskBuilder.setTask(task);

        Protocol.ServerRequest.Builder builder = Protocol.ServerRequest.newBuilder();
        builder.setSubmit(taskBuilder.build())
               .setRequestId(requestId++)
               .setClientId(clientId);

        sendRequest(builder.build());
    }

    public void sendSubscribeRequest(int taskId) throws IOException {
        Protocol.Subscribe.Builder subscribeBuilder = Protocol.Subscribe.newBuilder();
        subscribeBuilder.setTaskId(taskId);

        Protocol.ServerRequest.Builder builder = Protocol.ServerRequest.newBuilder();
        builder.setSubscribe(subscribeBuilder.build())
               .setRequestId(requestId++)
               .setClientId(clientId);

        sendRequest(builder.build());
    }

    public void sendListTasksRequest() throws IOException {
        Protocol.ListTasks.Builder listTasksBuilder = Protocol.ListTasks.newBuilder();

        Protocol.ServerRequest.Builder builder = Protocol.ServerRequest.newBuilder();
        builder.setList(listTasksBuilder.build())
               .setRequestId(requestId++)
               .setClientId(clientId);

        sendRequest(builder.build());
    }

    private Protocol.Task buildProtocolTask(long a, long b, long p, long m, long n) {
        return Protocol.Task.newBuilder()
                            .setA(buildProtocolTaskParam(a))
                            .setB(buildProtocolTaskParam(b))
                            .setP(buildProtocolTaskParam(p))
                            .setM(buildProtocolTaskParam(m))
                            .setN(n)
                            .build();
    }

    private Protocol.Task.Param buildProtocolTaskParam(long value) {
        return Protocol.Task.Param.newBuilder()
                                  .setValue(value)
                                  .build();
    }

}