package server.thread;

import communication.Protocol;
import server.task.Task;
import server.task.TaskHolder;

import java.net.Socket;

import static server.utils.Converter.convertToProtocolTask;

public class TasksListThread extends AbstractTaskThread {

    public TasksListThread(Socket socket, Protocol.ServerRequest serverRequest) {
        super(socket, serverRequest);
    }

    @Override
    public void run() {
        Protocol.ListTasksResponse.Builder responseBuilder = Protocol.ListTasksResponse.newBuilder();
        for (Task currentTask: TaskHolder.getTaskList()) {
            process(responseBuilder, currentTask);
        }
        sendResponceToClient(responseBuilder);
    }

    protected void sendResponceToClient(Protocol.ListTasksResponse.Builder responseBuilder) {
        responseBuilder.setStatus(Protocol.Status.OK);
        Protocol.ServerResponse.Builder serverResponseBuilder = Protocol.ServerResponse.newBuilder();
        serverResponseBuilder.setRequestId(serverRequest.getRequestId());
        serverResponseBuilder.setListResponse(responseBuilder.build());
        trySendResponse(serverResponseBuilder);
    }

    protected void process(Protocol.ListTasksResponse.Builder responseBuilder, Task currentTask) {
        Protocol.ListTasksResponse.TaskDescription.Builder builder = Protocol.ListTasksResponse.TaskDescription.newBuilder();
        builder.setTaskId(currentTask.id);
        builder.setClientId(currentTask.clientId);
        builder.setTask(convertToProtocolTask(currentTask));
        if (currentTask.isDone){
            builder.setResult(currentTask.result);
        }
        responseBuilder.addTasks(builder.build());
    }
}