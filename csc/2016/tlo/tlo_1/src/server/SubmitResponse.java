package server;

import communication.Protocol;
import communication.Protocol.ServerResponse;
import communication.Protocol.SubmitTaskResponse;

import java.net.Socket;

/**
 * Created by lt on 11.05.16.
 */
public class SubmitResponse extends AbstractResponse {
    private MyTask task;

    public SubmitResponse(MyTask task, Socket socket, long requestId, String clientID) {
        super(socket, requestId, clientID);
        this.task = task;
    }

    public ServerResponse get() {

        try {
            sleep(1000);
        } catch (InterruptedException e) {
            e.getStackTrace();
        }
        synchronized (task) {
            try {
                while (task.getTaskStatus() == MyTask.Status.RUNNING) {
                    task.wait();
                }
            } catch (InterruptedException e) {
                e.getStackTrace();
            } finally {
                task.notifyAll();
            }
        }
        Protocol.Status status;
        if (task.getTaskStatus() == MyTask.Status.ERROR) {
            status = Protocol.Status.ERROR;
        } else {
            status = Protocol.Status.OK;
        }

        SubmitTaskResponse submitResponse = SubmitTaskResponse.newBuilder()
                .setSubmittedTaskId(task.getTaskId())
                .setStatus(status)
                .build();

        return ServerResponse.newBuilder()
                .setRequestId(requestId)
                .setSubmitResponse(submitResponse)
                .build();
    }

}
