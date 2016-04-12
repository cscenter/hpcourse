package server;

import communication.Protocol;

import java.io.IOException;
import java.util.concurrent.Callable;

class RequestHandler implements Callable<Protocol.ServerResponse> {
    private final Protocol.ServerRequest request;
    private final TaskManager taskManager;

    RequestHandler(Protocol.ServerRequest serverRequest, TaskManager taskManager) {
        this.request = serverRequest;
        this.taskManager = taskManager;
    }

    @Override
    public Protocol.ServerResponse call() throws Exception {
        Protocol.ServerResponse.Builder responseBuilder = Protocol.ServerResponse.newBuilder();
        responseBuilder.setRequestId(request.getRequestId());

        if (request.hasSubmit()) {
            Protocol.Task task = request.getSubmit().getTask();
            int id = taskManager.addTask(task);

            Protocol.SubmitTaskResponse.Builder submitResponse = Protocol.SubmitTaskResponse.newBuilder();
            submitResponse.setSubmittedTaskId(id).setStatus(Protocol.Status.OK);
            responseBuilder.setSubmitResponse(submitResponse);
        }
        if (request.hasSubscribe()) {
            Protocol.Subscribe subscribe = request.getSubscribe();
            int id = subscribe.getTaskId();
            long result = taskManager.getResult(id);

            Protocol.SubscribeResponse.Builder builder = Protocol.SubscribeResponse.newBuilder();
            builder.setStatus(Protocol.Status.OK).setValue(result);
            responseBuilder.setSubscribeResponse(builder);
        }
        if (request.hasList()) {
            Protocol.ListTasksResponse.Builder builder = Protocol.ListTasksResponse.newBuilder();

            for (Integer id : taskManager.getRunningTasks()) {
                Protocol.ListTasksResponse.TaskDescription.Builder taskDescBuilder = Protocol.ListTasksResponse.TaskDescription.newBuilder();
                taskDescBuilder.setClientId(request.getClientId())
                        .setTaskId(id)
                        .setTask(taskManager.getTask(id));
                builder.addTasks(taskDescBuilder);
            }
        }
        return responseBuilder.build();
    }
}
