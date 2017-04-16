package server;

import protocol.Protocol;
import task.Task;

import java.io.IOException;
import java.net.Socket;

public class TaskListThread extends AbstractTaskThread {
    public TaskListThread(Socket socket, Protocol.ServerRequest serverRequest) {
        super(socket, serverRequest);
    }

    @Override
    public void run() {
        Protocol.ListTaskResponse.Builder listTask = Protocol.ListTaskResponse.newBuilder();

        for (Task task : TaskManager.getTasks()) {
            Protocol.ListTaskResponse.TaskDescription.Builder taskDescription = Protocol.ListTaskResponse.TaskDescription.newBuilder();
            taskDescription.setTaskId(task.getTaskId());
            taskDescription.setTask(task.toProtocolTask());
            if (task.isReady()) {
                taskDescription.setResult(task.getResult());
            }
            listTask.addTasks(taskDescription.build());
        }

        listTask.setStatus(Protocol.Status.OK);

        Protocol.ServerResponse.Builder serverResponse = Protocol.ServerResponse.newBuilder();
        serverResponse.setRequestId(serverRequest.getRequestId());
        serverResponse.setListResponse(listTask.build());

        try {
            sendResponse(serverResponse.build());
        } catch (IOException e) {
            e.printStackTrace();
            try {
                socket.close();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
    }
}
