import communication.Protocol;

import java.net.Socket;
import java.util.HashMap;


public class SubmitTask extends BaseTask {
    private String clientId;
    private Protocol.Status status;
    private long a, b, p, m, n;
    private long result;
    private final int taskId;
    private Protocol.Task task;
    private HashMap<Integer, SubmitTask> mapOfTasks;
    private HashMap<Integer, Object> tableOfMutex;

    // 1 - не решена, 2 - решена
    private int statusOfTheTask;

    SubmitTask(Socket socket, long requestId, String clientId, Protocol.Task task, int taskId,
                   HashMap<Integer, SubmitTask> mapOfTasks,
                    HashMap<Integer, Object> tableOfMutex) {
        super(socket, requestId);

        this.taskId = taskId;
        this.task = task;
        this.status = Protocol.Status.OK;
        this.clientId = clientId;
        this.mapOfTasks = mapOfTasks;
        this.tableOfMutex = tableOfMutex;
        this.statusOfTheTask = 1;

        synchronized (mapOfTasks) {
            if(mapOfTasks.containsKey(taskId) && mapOfTasks.get(taskId).getStatusOfResult() == 2){
                sendResponse();
            } else {
                mapOfTasks.put(taskId, this);
                synchronized (tableOfMutex) {
                    tableOfMutex.put(taskId, new Object());
                }
            }
        }

        start();
    }

    @Override
    public void run() {
        try {
            getAllParams();

            if(status == Protocol.Status.OK) {
                calculateResult();
                statusOfTheTask = 2;
            }

        } catch (InterruptedException e) {
            status = Protocol.Status.ERROR;
            e.printStackTrace();
        } finally {
            sendResponse();
        }
    }

    private void sendResponse() {
        Protocol.SubmitTaskResponse.Builder builder = Protocol.SubmitTaskResponse.newBuilder();
        builder.setStatus(status);
        builder.setSubmittedTaskId(taskId);
        sendResponse(builder.build());
    }

    private void getAllParams() throws InterruptedException{
        a = getDependentParam(task.getA());
        b = getDependentParam(task.getB());
        p = getDependentParam(task.getP());
        m = getDependentParam(task.getM());
        n = task.getN();
        if(n == 0){
            status = Protocol.Status.ERROR;
        }
    }

    private long getDependentParam(Protocol.Task.Param param) throws InterruptedException{
        if(param.hasValue()){
            return param.getValue();
        } else {
            if(!mapOfTasks.containsKey(new Long(param.getDependentTaskId()))) {
                status = Protocol.Status.ERROR;
                return -1;
            } else {
                long id = param.getDependentTaskId();

                synchronized (tableOfMutex.get(id)) {
                    if(mapOfTasks.get(id).getStatusOfResult() != 2){
                        tableOfMutex.get(id).wait();
                    }
                    return mapOfTasks.get(id).getResult();
                }
            }
        }
    }

    private void calculateResult() {

        while (n-- > 0) {
            b = (a * p + b) % m;
            a = b;
        }
        result = a;
    }

    public int getTaskId() {
        return taskId;
    }

    public String getClientId() {
        return clientId;
    }

    public Protocol.Task getTask() {
        return task;
    }

    public Protocol.Status getStatus() {
        return status;
    }

    public int getStatusOfResult() {
        return statusOfTheTask;
    }

    public long getResult() {
        return result;
    }

}
