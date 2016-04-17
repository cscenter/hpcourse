package com.ashatta.hps.server;

import static org.junit.Assert.fail;

import com.ashatta.hps.communication.Protocol;
import com.ashatta.hps.communication.Protocol.WrapperMessage;
import javafx.util.Pair;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.Assert;

import java.io.IOException;
import java.net.Socket;
import java.util.*;

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
    private long requestCounter;

    @Before
    public void setUp() throws Exception{
        server = new Server(PortFinder.findFreePort());
        requestCounter = 0;
        (new Thread(server)).start();
        Thread.sleep(200);
    }

    @After
    public void shutdown() {
        server.stop();
    }

    @Test
    public void testSubmit() throws Exception {
        Protocol.WrapperMessage message =
                buildSubmitTaskMessage(buildIntParam(10), buildIntParam(12), buildIntParam(5), buildIntParam(3), 5L).getKey();

        Client client = new Client("localhost", server.getPort());
        client.sendWrappedMessage(message);
        Protocol.WrapperMessage responseMessage = client.receive();
        Protocol.ServerResponse response = responseMessage.getResponse();

        Assert.assertEquals(0, response.getRequestId());
        Assert.assertEquals(Protocol.Status.OK, response.getSubmitResponse().getStatus());
    }

    @Test
    public void testSubscribe() throws Exception {
        Protocol.WrapperMessage message =
                buildSubmitTaskMessage(buildIntParam(10), buildIntParam(12), buildIntParam(5), buildIntParam(3), 5L).getKey();

        Client client = new Client("localhost", server.getPort());
        client.sendWrappedMessage(message);
        int taskId = client.receive().getResponse().getSubmitResponse().getSubmittedTaskId();
        message = Protocol.WrapperMessage.newBuilder()
                .setRequest(Protocol.ServerRequest.newBuilder()
                        .setClientId("SimpleClient")
                        .setRequestId(requestCounter++)
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
        Protocol.WrapperMessage message =
                buildSubmitTaskMessage(buildIntParam(10), buildIntParam(12), buildIntParam(5), buildIntParam(3), 5L).getKey();

        Client client = new Client("localhost", server.getPort());
        client.sendWrappedMessage(message);
        int taskId1 = client.receive().getResponse().getSubmitResponse().getSubmittedTaskId();
        message = buildSubmitTaskMessage(buildIntParam(13), buildIntParam(12), buildIntParam(7), buildIntParam(8), 5L).getKey();
        client.sendWrappedMessage(message);
        int taskId2 = client.receive().getResponse().getSubmitResponse().getSubmittedTaskId();
        message = Protocol.WrapperMessage.newBuilder()
                .setRequest(Protocol.ServerRequest.newBuilder()
                        .setClientId("SimpleClient")
                        .setRequestId(requestCounter++)
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

    @Test
    public void testIndependentTasks() throws IOException {
        Map<Long, Long> expectedOutputs = new HashMap<>();
        Map<Long, Long> subscribeToSubmit = new HashMap<>();

        long[] aList = { 10, 5, 39, 38, 27, 63, 25, 64, 92, 99 };
        long[] bList = { 12, 20, 140, 2394, 2938, 12, 252, 22, 90, 59 };
        long[] pList = { 5, 17, 123, 1, 298, 33, 299, 22, 11, 9 };
        long[] mList = { 30, 9, 3, 198, 39, 2988, 11, 4, 21, 28 };
        long n = 5000000;

        Pair<WrapperMessage, Long> messageIdPair;
        WrapperMessage message;

        Client client = new Client("localhost", server.getPort());
        for (int i = 0; i < aList.length; ++i) {
            messageIdPair = buildSubmitTaskMessage(
                    buildIntParam(aList[i]),
                    buildIntParam(bList[i]),
                    buildIntParam(pList[i]),
                    buildIntParam(mList[i]),
                    n);
            message = messageIdPair.getKey();
            expectedOutputs.put(messageIdPair.getValue(), compute(aList[i], bList[i], pList[i], mList[i], n));
            client.sendWrappedMessage(message);
        }

        for (int i = 0; i < aList.length*2; ++i) {
            Protocol.ServerResponse response = client.receive().getResponse();
            if (response.hasSubmitResponse()) {
                int taskId = response.getSubmitResponse().getSubmittedTaskId();
                subscribeToSubmit.put(requestCounter, response.getRequestId());

                message = Protocol.WrapperMessage.newBuilder()
                        .setRequest(Protocol.ServerRequest.newBuilder()
                                .setClientId("SimpleClient")
                                .setRequestId(requestCounter++)
                                .setSubscribe(Protocol.Subscribe.newBuilder()
                                        .setTaskId(taskId))).build();
                client.sendWrappedMessage(message);
            } else if (response.hasSubscribeResponse()) {
                Assert.assertEquals(Protocol.Status.OK, response.getSubscribeResponse().getStatus());
                Assert.assertEquals((long) expectedOutputs.get(subscribeToSubmit.get(response.getRequestId())),
                        response.getSubscribeResponse().getValue());
            } else {
                fail("Unexpected response type");
            }
        }

    }

    private Pair<Protocol.WrapperMessage, Long> buildSubmitTaskMessage(Protocol.Task.Param a, Protocol.Task.Param b,
                                                           Protocol.Task.Param p, Protocol.Task.Param m, long n)
    {
        return new Pair<>(
                Protocol.WrapperMessage.newBuilder()
                        .setRequest(Protocol.ServerRequest.newBuilder()
                                .setClientId("SimpleClient")
                                .setRequestId(requestCounter++)
                                .setSubmit(Protocol.SubmitTask.newBuilder()
                                        .setTask(Protocol.Task.newBuilder()
                                                .setA(a)
                                                .setB(b)
                                                .setP(p)
                                                .setM(m)
                                                .setN(n)))).build(),
                requestCounter - 1);
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
