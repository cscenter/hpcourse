package server;

import communication.Protocol.SubscribeResponse;
import communication.Protocol.ServerResponse;
import communication.Protocol.Status;

/**
 * Created by andrey on 13.04.16.
 */
public class ServerSubscribeResponse extends AbstractServerResponse {
    private ServerTask task;

    ServerSubscribeResponse(long requestID, ServerTask task) {
        super(requestID, task.getClientID());
        this.task = task;
    }

    @Override
    protected final ServerResponse getResponse() {
        synchronized(task) {
            try {
                while (task.getStatus() != ServerTask.Status.COMPLETE && task.getStatus() != ServerTask.Status.ERROR) {
                    System.out.println("Subscribe response waiting for complete task " + task.getID());
                    task.wait();
                }
            } finally {
                task.notifyAll();

                Status s = Status.OK;
                if (task.getStatus() == ServerTask.Status.ERROR) {
                    s = Status.ERROR;
                }

                SubscribeResponse subscribeResponse = SubscribeResponse.newBuilder()
                        .setStatus(s)
                        .setValue(task.getResult())
                        .build();

                return ServerResponse.newBuilder()
                        .setRequestId(requestID)
                        .setSubscribeResponse(subscribeResponse)
                        .build();
            }
        }
    }
}
