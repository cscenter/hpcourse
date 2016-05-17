package server;

import communication.Protocol;
import javafx.util.Pair;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.List;
import java.util.Map;

/**
 * Created by Helen on 10.04.2016.
 */
public class ExecuteRequest implements Runnable {
    Socket socket;
    TaskManager manager;

    public ExecuteRequest(Socket socket, TaskManager manager) {
        this.socket = socket;
        this.manager = manager;
    }

    @Override
    public void run() {
        try (InputStream in = socket.getInputStream();
             OutputStream out = socket.getOutputStream()) {
            Protocol.WrapperMessage wrapperMessage = Protocol.WrapperMessage.parseDelimitedFrom(in);
            Protocol.ServerRequest request = wrapperMessage.getRequest();

            Protocol.ServerResponse.Builder response = Protocol.ServerResponse.newBuilder();
            response.setRequestId(request.getRequestId());

            if (request.hasSubmit()) {
                Protocol.Task task = request.getSubmit().getTask();
                Protocol.SubmitTaskResponse.Builder submitResponse = Protocol.SubmitTaskResponse.newBuilder();
                try {
                    if (checkDependencies(task)) {
                        int taskId = manager.CreateTask(request.getClientId(), task);
                        submitResponse.setSubmittedTaskId(taskId).setStatus(Protocol.Status.OK);
                        response.setSubmitResponse(submitResponse);

                        new Thread(new TaskRunnable(taskId, manager)).start();
                    } else {
                        response.setSubmitResponse(submitResponse.setSubmittedTaskId(-1).setStatus(Protocol.Status.ERROR));
                    }
                } catch (Exception ignored) {
                    response.setSubmitResponse(submitResponse.setSubmittedTaskId(-1).setStatus(Protocol.Status.ERROR));
                }
            } else if (request.hasSubscribe()) {
                Protocol.SubscribeResponse.Builder subscribeResponse = Protocol.SubscribeResponse.newBuilder();
                try {
                    long result = manager.getTaskResult(request.getSubscribe().getTaskId());
                    subscribeResponse.setValue(result).setStatus(Protocol.Status.OK);
                    response.setSubscribeResponse(subscribeResponse);
                } catch (Exception ignored) {
                    response.setSubscribeResponse(subscribeResponse.setValue(0).setStatus(Protocol.Status.ERROR));
                }
            } else if (request.hasList()) {
                Protocol.ListTasksResponse.Builder listResponse = Protocol.ListTasksResponse.newBuilder()
                        .setStatus(Protocol.Status.OK);
                try {
                    for (Pair<Integer, Task> entry : manager.getTaskMap()) {
                        Protocol.ListTasksResponse.TaskDescription.Builder builder = Protocol.ListTasksResponse
                                .TaskDescription.newBuilder();
                        Task task = entry.getValue();
                        builder.setTask(task.getTaskInfo()).setTaskId(entry.getKey()).setClientId(task.getClientID());
                        if (task.getState() == Task.State.FINISHED)
                            builder.setResult(task.result.get());
                        listResponse.addTasks(builder);
                    }
                    response.setListResponse(listResponse);
                } catch (Exception ignored) {
                    response.setListResponse(listResponse.setStatus(Protocol.Status.ERROR));
                }
            }

            Protocol.WrapperMessage.newBuilder().setResponse(response).build().writeDelimitedTo(out);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean checkDependencies(Protocol.Task task) {
        Protocol.Task.Param[] parameters = new Protocol.Task.Param[]{task.getA(), task.getB(), task.getM(), task.getP()};
        for (Protocol.Task.Param param : parameters) {
            if (param.hasDependentTaskId()) {
                if (!manager.hasTask(param.getDependentTaskId()))
                    return false;
            }
        }
        return true;
    }
}
