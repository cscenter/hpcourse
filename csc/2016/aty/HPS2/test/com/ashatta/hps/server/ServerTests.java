package com.ashatta.hps.server;

import static org.junit.Assert.assertEquals;
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

        public void sendWrappedMessage(WrapperMessage message) throws IOException {
            message.writeDelimitedTo(socket.getOutputStream());
        }

        public WrapperMessage receive() throws IOException {
            return WrapperMessage.parseDelimitedFrom(socket.getInputStream());
        }
    }

    private Server server;
    private long requestCounter;

    @Before
    public void setUp() throws InterruptedException {
        server = new Server(PortFinder.findFreePort(), 10);
        requestCounter = 0;
        (new Thread(server)).start();
        Thread.sleep(200);
    }

    @After
    public void shutdown() throws InterruptedException {
        server.stop();
    }

    @Test
    public void testSubmit() throws Exception {
        WrapperMessage message =
                buildSubmitTaskMessage(buildIntParam(10), buildIntParam(12), buildIntParam(5), buildIntParam(3), 5L).getKey();

        Client client = new Client("localhost", server.getPort());
        client.sendWrappedMessage(message);
        WrapperMessage responseMessage = client.receive();
        Protocol.ServerResponse response = responseMessage.getResponse();

        Assert.assertEquals(0, response.getRequestId());
        Assert.assertEquals(Protocol.Status.OK, response.getSubmitResponse().getStatus());
    }

    @Test
    public void testSubscribe() throws Exception {
        WrapperMessage message =
                buildSubmitTaskMessage(buildIntParam(10), buildIntParam(12), buildIntParam(5), buildIntParam(3), 5L).getKey();

        Client client = new Client("localhost", server.getPort());
        client.sendWrappedMessage(message);
        int taskId = client.receive().getResponse().getSubmitResponse().getSubmittedTaskId();
        message = WrapperMessage.newBuilder()
                .setRequest(Protocol.ServerRequest.newBuilder()
                        .setClientId("SimpleClient")
                        .setRequestId(requestCounter++)
                        .setSubscribe(Protocol.Subscribe.newBuilder()
                            .setTaskId(taskId))).build();
        client.sendWrappedMessage(message);
        WrapperMessage responseMessage = client.receive();
        Protocol.ServerResponse response = responseMessage.getResponse();

        Assert.assertEquals(1, response.getRequestId());
        Assert.assertEquals(Protocol.Status.OK, response.getSubscribeResponse().getStatus());
        Assert.assertEquals(compute(10, 12, 5, 3, 5), response.getSubscribeResponse().getValue());
    }

    @Test
    public void testListTasks() throws Exception {
        WrapperMessage message =
                buildSubmitTaskMessage(buildIntParam(10), buildIntParam(12), buildIntParam(5), buildIntParam(3), 5L).getKey();

        Client client = new Client("localhost", server.getPort());
        client.sendWrappedMessage(message);
        int taskId1 = client.receive().getResponse().getSubmitResponse().getSubmittedTaskId();
        message = buildSubmitTaskMessage(buildIntParam(13), buildIntParam(12), buildIntParam(7), buildIntParam(8), 5L).getKey();
        client.sendWrappedMessage(message);
        int taskId2 = client.receive().getResponse().getSubmitResponse().getSubmittedTaskId();
        message = WrapperMessage.newBuilder()
                .setRequest(Protocol.ServerRequest.newBuilder()
                        .setClientId("SimpleClient")
                        .setRequestId(requestCounter++)
                        .setList(Protocol.ListTasks.getDefaultInstance())).build();
        client.sendWrappedMessage(message);
        WrapperMessage responseMessage = client.receive();
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
        Set<WrapperMessage> messages = new HashSet<>();

        long[] aList = { 10, 5, 39, 38, 27, 63, 25, 64, 92, 99 };
        long[] bList = { 12, 20, 140, 2394, 2938, 12, 252, 22, 90, 59 };
        long[] pList = { 5, 17, 123, 1, 298, 33, 299, 22, 11, 9 };
        long[] mList = { 30, 9, 3, 198, 39, 2988, 11, 4, 21, 28 };
        long n = 5000000;

        Client client = new Client("localhost", server.getPort());
        for (int i = 0; i < aList.length; ++i) {
            Pair<WrapperMessage, Long> messageIdPair = buildSubmitTaskMessage(
                    buildIntParam(aList[i]),
                    buildIntParam(bList[i]),
                    buildIntParam(pList[i]),
                    buildIntParam(mList[i]),
                    n);
            messages.add(messageIdPair.getKey());
            expectedOutputs.put(messageIdPair.getValue(), compute(aList[i], bList[i], pList[i], mList[i], n));
        }

        for (WrapperMessage message : messages) {
            client.sendWrappedMessage(message);
        }

        for (int i = 0; i < aList.length*2; ++i) {
            Protocol.ServerResponse response = client.receive().getResponse();
            if (response.hasSubmitResponse()) {
                int taskId = response.getSubmitResponse().getSubmittedTaskId();
                subscribeToSubmit.put(requestCounter, response.getRequestId());

                WrapperMessage message = WrapperMessage.newBuilder()
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

    @Test
    public void testDependentTasks() throws IOException {
        /* Consists of five tasks: first three are independent of each other,
            the fourth one is dependent on the first and the second,
            the fifth one is dependent on the second and the third.
         */
        List<Long> results = new ArrayList<>();
        Map<Long, Integer> subscribeToSubmit = new HashMap<>();

        long[] aList = { 10, 5, 39 };
        long[] bList = { 84, 12, 3 };
        long[] pList = { 29, 134, 6 };
        long[] mList = { 17, 2039, 11 };
        long n = 5000000;

        Client client = new Client("localhost", server.getPort());
        for (int i = 0; i < aList.length; ++i) {
            results.add(compute(aList[i], bList[i], pList[i], mList[i], n));
        }
        results.add(compute(results.get(0), 54, results.get(1), 38, n));
        results.add(compute(298, results.get(1), results.get(2), 11, n));

        for (int i = 0; i < aList.length; ++i) {
            client.sendWrappedMessage(buildSubmitTaskMessage(
                    buildIntParam(aList[i]),
                    buildIntParam(bList[i]),
                    buildIntParam(pList[i]),
                    buildIntParam(mList[i]),
                    n).getKey());
        }

        int[] taskIds = new int[5];
        for (int i = 0; i < aList.length; ++i) {
            Protocol.ServerResponse response = client.receive().getResponse();
            taskIds[(int) response.getRequestId()] = response.getSubmitResponse().getSubmittedTaskId();
        }

        client.sendWrappedMessage(buildSubmitTaskMessage(
                buildIdParam(taskIds[0]),
                buildIntParam(54),
                buildIdParam(taskIds[1]),
                buildIntParam(38),
                n).getKey());

        client.sendWrappedMessage(buildSubmitTaskMessage(
                buildIntParam(298),
                buildIdParam(taskIds[1]),
                buildIdParam(taskIds[2]),
                buildIntParam(11),
                n).getKey());

        for (int i = aList.length; i < taskIds.length; ++i) {
            Protocol.ServerResponse response = client.receive().getResponse();
            taskIds[(int) response.getRequestId()] = response.getSubmitResponse().getSubmittedTaskId();
        }

        for (int i = 0; i < taskIds.length; ++i) {
            subscribeToSubmit.put(requestCounter, i);
            WrapperMessage message = WrapperMessage.newBuilder()
                    .setRequest(Protocol.ServerRequest.newBuilder()
                            .setClientId("SimpleClient")
                            .setRequestId(requestCounter++)
                            .setSubscribe(Protocol.Subscribe.newBuilder()
                                    .setTaskId(taskIds[i]))).build();
            client.sendWrappedMessage(message);
        }

        for (int i = 0; i < taskIds.length; ++i) {
            Protocol.ServerResponse response = client.receive().getResponse();
            assertEquals((long) results.get(subscribeToSubmit.get(response.getRequestId())),
                response.getSubscribeResponse().getValue());
        }
    }

    @Test
    public void testDivisionByZero() throws IOException {
        WrapperMessage message =
                buildSubmitTaskMessage(buildIntParam(10), buildIntParam(12), buildIntParam(5), buildIntParam(0), 5L).getKey();

        Client client = new Client("localhost", server.getPort());
        client.sendWrappedMessage(message);
        int taskId = client.receive().getResponse().getSubmitResponse().getSubmittedTaskId();
        message = WrapperMessage.newBuilder()
                .setRequest(Protocol.ServerRequest.newBuilder()
                        .setClientId("SimpleClient")
                        .setRequestId(requestCounter++)
                        .setSubscribe(Protocol.Subscribe.newBuilder()
                                .setTaskId(taskId))).build();
        client.sendWrappedMessage(message);
        WrapperMessage responseMessage = client.receive();
        Protocol.ServerResponse response = responseMessage.getResponse();

        Assert.assertEquals(1, response.getRequestId());
        Assert.assertEquals(Protocol.Status.ERROR, response.getSubscribeResponse().getStatus());
    }

    private Pair<WrapperMessage, Long> buildSubmitTaskMessage(Protocol.Task.Param a, Protocol.Task.Param b,
                                                           Protocol.Task.Param p, Protocol.Task.Param m, long n) {
        return new Pair<>(
                WrapperMessage.newBuilder()
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
