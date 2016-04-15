package server;

import communication.Protocol.ServerResponse;
import communication.Protocol.SubmitTaskResponse;
import communication.Protocol.Status;

/**
 * Created by andrey on 12.04.16.
 */
public class ServerSubmitResponse extends AbstractServerResponse {
    private ServerTask task;

    ServerSubmitResponse(long requestID, ServerTask task) {
        super(requestID, task.getClientID());
        this.task = task;
    }

    @Override
    protected final ServerResponse getResponse() {
        synchronized(task) {
            try {
                while (task.getStatus() != ServerTask.Status.COMPLETE &&
                        task.getStatus() != ServerTask.Status.RUN &&
                        task.getStatus() != ServerTask.Status.ERROR)
                {
                    System.out.println("Submit response waiting for complete task  " + task.getID());
                    task.wait();
                }
            } finally {
                task.notifyAll(); // report other threads

                Status s = Status.OK;
                if (task.getStatus() == ServerTask.Status.ERROR) {
                    s = Status.ERROR;
                }

                SubmitTaskResponse sResponse = SubmitTaskResponse.newBuilder()
                        .setStatus(s)
                        .setSubmittedTaskId(task.getID())
                        .build();

                return ServerResponse.newBuilder()
                        .setRequestId(requestID)
                        .setSubmitResponse(sResponse)
                        .build();
            }
        }
    }
}
