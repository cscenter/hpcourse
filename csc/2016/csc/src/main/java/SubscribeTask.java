import communication.Protocol;

import java.net.Socket;
import java.util.HashMap;


public class SubscribeTask extends BaseTask {

    private int taskId;
    private HashMap<Integer, SubmitTask> tableOfTasks;
    private HashMap<Integer, Object> tableOfMutex;

    SubscribeTask(Socket socket, long requestId, Protocol.Subscribe subscribe,
                  HashMap<Integer, SubmitTask> tableOfTasks,
                  HashMap<Integer, Object> tableOfMutex) {
        super(socket, requestId);
        this.taskId = subscribe.getTaskId();
        this.tableOfTasks = tableOfTasks;
        this.tableOfMutex = tableOfMutex;
        start();
    }

    @Override
    public void run() {
        Protocol.SubscribeResponse.Builder builder = Protocol.SubscribeResponse.newBuilder();
        try {
            if(tableOfTasks.containsKey(taskId)){
                builder.setStatus(Protocol.Status.OK);
                synchronized (tableOfMutex.get(taskId)) {
                    if(tableOfTasks.get(taskId).getStatusOfResult() != 2) {
                        tableOfMutex.get(taskId).wait();
                    }
                    builder.setValue(tableOfTasks.get(taskId).getResult());
                }
            } else {
                builder.setStatus(Protocol.Status.ERROR);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            sendResponse(builder.build());
        }
    }
}
