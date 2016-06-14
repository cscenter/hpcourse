package client;

import communication.Protocol;
import javafx.util.Pair;
import util.FutureValue;
import util.ProtocolUtils;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

/**
 * Created by nikita.sokeran@gmail.com
 */
public class ClientServerTest {

    protected Pair<Long, Integer> sendAndGetResultAndId(final Client client, final Protocol.Task task) throws IOException, InterruptedException {
        final Protocol.SubmitTask submitTask = ProtocolUtils.createSubmitTask(task);
        final FutureValue<Protocol.SubmitTaskResponse> submitFuture = client.sendServerRequest(submitTask);
        final Protocol.SubmitTaskResponse taskResponse = submitFuture.get();

        assertEquals(Protocol.Status.OK, taskResponse.getStatus());

        final int taskId = taskResponse.getSubmittedTaskId();
        final Protocol.Subscribe subscribe = Protocol.Subscribe.newBuilder().setTaskId(taskId).build();
        final FutureValue<Protocol.SubscribeResponse> subscribeFuture = client.sendServerRequest(subscribe);
        final Protocol.SubscribeResponse subscribeResponse = subscribeFuture.get();
        return new Pair<>(subscribeResponse.getValue(), taskId);
    }
}
