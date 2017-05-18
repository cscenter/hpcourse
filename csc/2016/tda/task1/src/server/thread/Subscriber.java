package server.thread;

import server.storage.TaskStorage;

import java.net.Socket;

import static communication.Protocol.*;

/**
 * Thread for subscribe task
 * @author Dmitriy Tseyler
 */
class Subscriber extends AbstractServerThread<SubscribeResponse> {

    private final int taskId;

    Subscriber(Socket socket, long requestId, String clientId, TaskStorage storage, int taskId) {
        super(socket, requestId, storage, clientId, ServerResponse.Builder::setSubscribeResponse);
        this.taskId = taskId;
    }

    @Override
    public void run() {
        SubscribeResponse.Builder builder = SubscribeResponse.newBuilder();
        try {
            Calculator calculator = getStorage().getCalculator(taskId);
            Status status = calculator.getStatus();
            builder.setStatus(status);
            if (status == Status.OK)
                builder.setValue(calculator.getValue());
        } catch (InterruptedException e) {
            builder.setStatus(Status.ERROR);
        }
        response(builder.build());
    }
}
