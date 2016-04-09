package client;

import communication.Protocol;
import server.TaskManager;

import java.io.*;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * Created by dkorolev on 4/5/2016.
 */
public class MyClient {
    private final String clientId;
    private final String hostName;
    private final int port;
    private final AtomicLong requestIdProducer;

    public MyClient(String clientId, String hostName, int port) {
        this.clientId = clientId;
        this.hostName = hostName;
        this.port = port;
        this.requestIdProducer = new AtomicLong();
    }

    public int submitTask(Protocol.Task task) {
        Protocol.ServerRequest.Builder requestBuilder = Protocol.ServerRequest.newBuilder().
                setSubmit(Protocol.SubmitTask.newBuilder().
                        setTask(task));
        Protocol.ServerResponse response = connect(requestBuilder);

        return response.getSubmitResponse().getSubmittedTaskId();
    }

    public Long subscribe(int taskId) {
        Protocol.ServerRequest.Builder requestBuilder = Protocol.ServerRequest.newBuilder().
                setSubscribe(Protocol.Subscribe.newBuilder().
                        setTaskId(taskId));
        Protocol.ServerResponse response = connect(requestBuilder);
        Protocol.SubscribeResponse subscribeResponse = response.getSubscribeResponse();
        if (subscribeResponse.getStatus().equals(Protocol.Status.OK)) {
            return subscribeResponse.getValue();
        }

        return null;
    }

    public List<Protocol.ListTasksResponse.TaskDescription> getList() {
        Protocol.ServerRequest.Builder requestBuilder = Protocol.ServerRequest.newBuilder().
                setList(Protocol.ListTasks.newBuilder());
        Protocol.ServerResponse response = connect(requestBuilder);

        return response.getListResponse().getTasksList();
    }

    private Protocol.ServerResponse connect(Protocol.ServerRequest.Builder requestBuilder) {
        try (
                Socket socket = new Socket(hostName, port);
                InputStream in = socket.getInputStream();
                OutputStream out = socket.getOutputStream()
        ) {
            requestBuilder.setClientId(clientId);
            requestBuilder.setRequestId(requestIdProducer.getAndIncrement());
            Protocol.WrapperMessage wrapperMessage = Protocol.WrapperMessage.newBuilder().setRequest(requestBuilder).build();
            wrapperMessage.writeDelimitedTo(out);
            out.flush();

            Protocol.WrapperMessage responseMessage = Protocol.WrapperMessage.parseFrom(in);

            return responseMessage.getResponse();
        } catch (UnknownHostException e) {
            System.err.println("Don't know about host " + hostName);
        } catch (IOException e) {
            System.err.println("Couldn't get I/O for the connection to " +
                    hostName);
        }

        return null;
    }
}
