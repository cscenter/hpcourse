package server;

import communication.Protocol;
import communication.Protocol.ServerResponse;

import java.net.Socket;

/**
 * Created by lt on 11.05.16.
 */
public class SubscribeResponse extends AbstractResponse {

    private MyTask task;

    public SubscribeResponse(MyTask task, Socket socket, long requestId) {
        super(socket, requestId);
        this.task = task;
    }


    //что если такой задачи нет или она в конструкторе
    public ServerResponse get() {

        Protocol.SubscribeResponse.Builder subscribeResponse = Protocol.SubscribeResponse
                .newBuilder();
        if (task == null) {
            subscribeResponse.setStatus(Protocol.Status.ERROR);
        } else {
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
            if (task.getTaskStatus() == MyTask.Status.ERROR) {
                subscribeResponse.setStatus(Protocol.Status.ERROR);
            } else {
                subscribeResponse.setStatus(Protocol.Status.OK);
                subscribeResponse.setValue(task.getResult());
            }
        }
        return ServerResponse
                .newBuilder()
                .setSubscribeResponse(subscribeResponse)
                .setRequestId(requestId)
                .build();

    }
}
