package server;

import communication.Protocol;
import concurrent.MyCallable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Map;

/**
 * Created by dkorolev on 4/2/2016.
 */
public class MyServerRunnable implements MyCallable {
    private final Socket socket;
    private final TaskManager taskManager;

    public MyServerRunnable(Socket socket, TaskManager taskManager) {
        this.socket = socket;
        this.taskManager = taskManager;
    }

    @Override
    public Object call() {
        try (InputStream in = socket.getInputStream();
             OutputStream out = socket.getOutputStream()) {
            Protocol.WrapperMessage requestMessage = Protocol.WrapperMessage.parseDelimitedFrom(in);
            Protocol.ServerRequest request = requestMessage.getRequest();

            Protocol.ServerResponse.Builder response = Protocol.ServerResponse.newBuilder().
                    setRequestId(request.getRequestId());
            if (request.hasSubmit()) {
                Protocol.SubmitTask submit = request.getSubmit();
                int taskId = taskManager.submitTask(new TaskDescFull(request.getClientId(), submit.getTask()));
                Protocol.SubmitTaskResponse.Builder submitTaskResponse = Protocol.SubmitTaskResponse.newBuilder().
                        setSubmittedTaskId(taskId).
                        setStatus(Protocol.Status.OK);
                response.setSubmitResponse(submitTaskResponse);
            } else if (request.hasList()) {
                //Protocol.ListTasks list = request.getList();
                Map<Integer, TaskDescFull> resultMap = taskManager.getList();
                Protocol.ListTasksResponse.Builder listResponse = Protocol.ListTasksResponse.newBuilder();
                //System.out.println("Server: listSize = " + resultMap.size());
                for (Map.Entry<Integer, TaskDescFull> resultEntry : resultMap.entrySet()) {
                    Protocol.ListTasksResponse.TaskDescription.Builder task =
                            Protocol.ListTasksResponse.TaskDescription.newBuilder().
                            setClientId(resultEntry.getValue().clientId).
                            setTask(resultEntry.getValue().task).
                            setTaskId(resultEntry.getKey());
                    if (resultEntry.getValue().result != null) {
                        task.setResult(resultEntry.getValue().result);
                    }
                    listResponse.addTasks(task);
                }
                listResponse.setStatus(Protocol.Status.OK);
                response.setListResponse(listResponse);
            } else if (request.hasSubscribe()) {
                Protocol.Subscribe subscribe = request.getSubscribe();
                Protocol.SubscribeResponse.Builder subscribeResponse = Protocol.SubscribeResponse.newBuilder();
                try {
                    Long result = taskManager.getResult(subscribe.getTaskId());
                    if (result == null) {
                        subscribeResponse.
                                setValue(0).
                                setStatus(Protocol.Status.ERROR);
                    } else {
                        subscribeResponse.
                                setValue(result).
                                setStatus(Protocol.Status.OK);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    subscribeResponse.setStatus(Protocol.Status.ERROR);
                }
                response.setSubscribeResponse(subscribeResponse);
            } else {
                System.err.println("Unknown type of request: " + requestMessage);
            }
            Protocol.WrapperMessage responseMessage = Protocol.WrapperMessage.newBuilder().
                    setResponse(response).
                    build();

            responseMessage.writeTo(out);
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }
}
