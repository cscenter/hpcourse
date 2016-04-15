package client;

import communication.Protocol;
import javafx.util.Pair;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import server.Server;
import util.Functions;
import util.FutureValue;
import util.ProtocolUtils;

import java.io.IOException;
import java.net.SocketException;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Created by nikita.sokeran@gmail.com
 */
public class SimpleTest extends ClientServerTest {

    private static final String HOST = "localhost";

    private Server server;
    private Client client1;
    private Client client2;
    private Client client3;
    private Client client4;
    private Client client5;

    public SimpleTest() throws IOException {
        final int randomPort = new Random().nextInt(20000) + 10000;

        server = new Server(randomPort);
        server.start();

        client1 = new Client(HOST, randomPort, "test_client1");
        client2 = new Client(HOST, randomPort, "test_client2");
        client3 = new Client(HOST, randomPort, "test_client3");
        client4 = new Client(HOST, randomPort, "test_client4");
        client5 = new Client(HOST, randomPort, "test_client5");
    }

    @Test
    public void futureTest1() throws Exception {
        final long a = 1555L;
        final long b = 4555L;
        final long c = 5555L;
        final long m = 6555L;
        final long n = 8555L;
        final Long expectedResult1 = Functions.calculateModulo(a, b, c, m, n);

        final Protocol.Task task1 = ProtocolUtils.createTask(
                ProtocolUtils.createParam(a, null),
                ProtocolUtils.createParam(b, null),
                ProtocolUtils.createParam(c, null),
                ProtocolUtils.createParam(m, null),
                n
        );

        final Pair<Long, Integer> result1 = sendAndGetResultAndId(client1, task1);
        assertEquals(expectedResult1, result1.getKey());

        final Long expectedResult2 = Functions.calculateModulo(a, b, c, expectedResult1, n);
        final Protocol.Task task2 = ProtocolUtils.createTask(
                ProtocolUtils.createParam(a, null),
                ProtocolUtils.createParam(b, null),
                ProtocolUtils.createParam(c, null),
                ProtocolUtils.createParam(null, result1.getValue()),
                n
        );

        final Pair<Long, Integer> result2 = sendAndGetResultAndId(client1, task2);
        assertEquals(expectedResult2, result2.getKey());

        final Long expectedResult3 = Functions.calculateModulo(a, expectedResult2, c, expectedResult1, n);
        final Protocol.Task task3 = ProtocolUtils.createTask(
                ProtocolUtils.createParam(a, null),
                ProtocolUtils.createParam(null, result2.getValue()),
                ProtocolUtils.createParam(c, null),
                ProtocolUtils.createParam(null, result1.getValue()),
                n
        );

        final Pair<Long, Integer> result3 = sendAndGetResultAndId(client1, task3);
        assertEquals(expectedResult3, result3.getKey());

        final Long expectedResult4 = Functions.calculateModulo(expectedResult3, expectedResult2, c, expectedResult1, n);
        final Protocol.Task task4 = ProtocolUtils.createTask(
                ProtocolUtils.createParam(null, result3.getValue()),
                ProtocolUtils.createParam(null, result2.getValue()),
                ProtocolUtils.createParam(c, null),
                ProtocolUtils.createParam(null, result1.getValue()),
                n
        );

        final Pair<Long, Integer> result4 = sendAndGetResultAndId(client1, task4);
        assertEquals(expectedResult4, result4.getKey());

        client1.close();
        client2.close();

        try {
            final FutureValue<Protocol.ListTasksResponse> listTasksResponseFuture = client2.sendServerRequest(Protocol.ListTasks.newBuilder().build());
            final Protocol.ListTasksResponse listTasksResponse = listTasksResponseFuture.get();

            for (final Protocol.ListTasksResponse.TaskDescription taskDescription : listTasksResponse.getTasksList()) {
                if (taskDescription.getTaskId() == result1.getValue()) {
                    assertEquals(expectedResult1.longValue(), taskDescription.getResult());
                }
                if (taskDescription.getTaskId() == result2.getValue()) {
                    assertEquals(expectedResult2.longValue(), taskDescription.getResult());
                }
                if (taskDescription.getTaskId() == result3.getValue()) {
                    assertEquals(expectedResult3.longValue(), taskDescription.getResult());
                }
                if (taskDescription.getTaskId() == result4.getValue()) {
                    assertEquals(expectedResult4.longValue(), taskDescription.getResult());
                }
            }
        } catch (IOException ignored) {
            return;
        }
        fail();
    }

    @Test(expected = SocketException.class)
    public void sendRequestWithClosedClient() throws IOException {
        client1.close();
        client1.sendServerRequest(Protocol.ListTasks.newBuilder().build());
    }
}