package com.ashatta.hps.server;

import com.ashatta.hps.communication.Protocol;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.Assert;

import java.io.IOException;
import java.net.Socket;
import java.util.HashSet;
import java.util.Set;

public class ServerTests {
    class Client {
        private Socket socket;

        public Client(String serverAddress, int port) throws IOException {
            socket = new Socket(serverAddress, port);
        }

        public void sendWrappedMessage(Protocol.WrapperMessage message) throws IOException {
            message.writeDelimitedTo(socket.getOutputStream());
        }

        public Protocol.WrapperMessage receive() throws IOException {
            return Protocol.WrapperMessage.parseDelimitedFrom(socket.getInputStream());
        }
    }

    private Server server;

    @Before
    public void setUp() throws Exception{
        server = new Server(PortFinder.findFreePort());
        (new Thread(server)).start();
        Thread.sleep(200);
    }

    @After
    public void shutdown() {
        server.stop();
    }

    @Test
    public void testSubmit() throws Exception {
        Protocol.WrapperMessage message = Protocol.WrapperMessage.newBuilder()
                .setRequest(Protocol.ServerRequest.newBuilder()
                    .setClientId("SimpleClient")
                    .setRequestId(0)
                    .setSubmit(Protocol.SubmitTask.newBuilder()
                        .setTask(Protocol.Task.newBuilder()
                            .setA(buildIntParam(10))
                            .setB(buildIntParam(12))
                            .setP(buildIntParam(5))
                            .setM(buildIntParam(3))
                            .setN(5L)))).build();

        Client client = new Client("localhost", server.getPort());
        client.sendWrappedMessage(message);
        Protocol.WrapperMessage responseMessage = client.receive();
        Protocol.ServerResponse response = responseMessage.getResponse();

        Assert.assertEquals(0, response.getRequestId());
        Assert.assertEquals(Protocol.Status.OK, response.getSubmitResponse().getStatus());
    }

    @Test
    public void testSubscribe() throws Exception {
        Protocol.WrapperMessage message = Protocol.WrapperMessage.newBuilder()
                .setRequest(Protocol.ServerRequest.newBuilder()
                        .setClientId("SimpleClient")
                        .setRequestId(0)
                        .setSubmit(Protocol.SubmitTask.newBuilder()
                                .setTask(Protocol.Task.newBuilder()
                                        .setA(buildIntParam(10))
                                        .setB(buildIntParam(12))
                                        .setP(buildIntParam(5))
                                        .setM(buildIntParam(3))
                                        .setN(5L)))).build();

        Client client = new Client("localhost", server.getPort());
        client.sendWrappedMessage(message);
        int taskId = client.receive().getResponse().getSubmitResponse().getSubmittedTaskId();
        message = Protocol.WrapperMessage.newBuilder()
                .setRequest(Protocol.ServerRequest.newBuilder()
                        .setClientId("SimpleClient")
                        .setRequestId(1)
                        .setSubscribe(Protocol.Subscribe.newBuilder()
                            .setTaskId(taskId))).build();
        client.sendWrappedMessage(message);
        Protocol.WrapperMessage responseMessage = client.receive();
        Protocol.ServerResponse response = responseMessage.getResponse();

        Assert.assertEquals(1, response.getRequestId());
        Assert.assertEquals(Protocol.Status.OK, response.getSubscribeResponse().getStatus());
        Assert.assertEquals(compute(10, 12, 5, 3, 5), response.getSubscribeResponse().getValue());
    }

    @Test
    public void testListTasks() throws Exception {
        Protocol.WrapperMessage message = Protocol.WrapperMessage.newBuilder()
                .setRequest(Protocol.ServerRequest.newBuilder()
                        .setClientId("SimpleClient")
                        .setRequestId(0)
                        .setSubmit(Protocol.SubmitTask.newBuilder()
                                .setTask(Protocol.Task.newBuilder()
                                        .setA(buildIntParam(10))
                                        .setB(buildIntParam(12))
                                        .setP(buildIntParam(5))
                                        .setM(buildIntParam(3))
                                        .setN(5L)))).build();

        Client client = new Client("localhost", server.getPort());
        client.sendWrappedMessage(message);
        int taskId1 = client.receive().getResponse().getSubmitResponse().getSubmittedTaskId();
        message = Protocol.WrapperMessage.newBuilder()
                .setRequest(Protocol.ServerRequest.newBuilder()
                        .setClientId("SimpleClient")
                        .setRequestId(1)
                        .setSubmit(Protocol.SubmitTask.newBuilder()
                                .setTask(Protocol.Task.newBuilder()
                                        .setA(buildIntParam(13))
                                        .setB(buildIntParam(12))
                                        .setP(buildIntParam(7))
                                        .setM(buildIntParam(8))
                                        .setN(5L)))).build();
        client.sendWrappedMessage(message);
        int taskId2 = client.receive().getResponse().getSubmitResponse().getSubmittedTaskId();
        message = Protocol.WrapperMessage.newBuilder()
                .setRequest(Protocol.ServerRequest.newBuilder()
                        .setClientId("SimpleClient")
                        .setRequestId(2)
                        .setList(Protocol.ListTasks.getDefaultInstance())).build();
        client.sendWrappedMessage(message);
        Protocol.WrapperMessage responseMessage = client.receive();
        Protocol.ServerResponse response = responseMessage.getResponse();

        Assert.assertEquals(2, response.getRequestId());
        Assert.assertEquals(Protocol.Status.OK, response.getListResponse().getStatus());
        Set<Integer> taskIds = new HashSet<>();
        for (Protocol.ListTasksResponse.TaskDescription description : response.getListResponse().getTasksList()) {
            taskIds.add(description.getTaskId());
        }
        Assert.assertEquals(2, taskIds.size());
        Assert.assertTrue(taskIds.contains(taskId1));
        Assert.assertTrue(taskIds.contains(taskId2));
    }

    private Protocol.Task.Param buildIntParam(long value) {
        return Protocol.Task.Param.newBuilder().setValue(value).build();
    }

    private Protocol.Task.Param buildIdParam(int value) {
        return Protocol.Task.Param.newBuilder().setDependentTaskId(value).build();
    }

    private long compute(long a, long b, long p, long m, long n) {
        while (n-- > 0)
        {
            b = (a * p + b) % m;
            a = b;
        }
        return a;
    }
}
