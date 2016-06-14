package communication.Client;

import communication.Protocol;
import communication.Task;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by malinovsky239 on 15.04.2016.
 */
public class Client {
    private String clientId;
    private String host;
    private int port;
    private int requestsCnt;

    public Client(String clientId, String host, int portNumber) {
        this.clientId = clientId;
        this.host = host;
        this.port = portNumber;
        this.requestsCnt = 0;
    }

    private Protocol.ServerResponse sendRequest(Protocol.ServerRequest.Builder builder) {
        builder.setClientId(clientId);
        builder.setRequestId(requestsCnt++);
        Protocol.ServerRequest request = builder.build();
        try {
            Socket socket = new Socket(this.host, this.port);
            request.writeDelimitedTo(socket.getOutputStream());
            return Protocol.ServerResponse.parseDelimitedFrom(socket.getInputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public int SubmitTask(Task task) throws Exception {
        Protocol.SubmitTask.Builder submitTaskBuilder = Protocol.SubmitTask.newBuilder();
        submitTaskBuilder.setTask(task.getBuilder());

        Protocol.ServerRequest.Builder builder = Protocol.ServerRequest.newBuilder();
        builder.setSubmit(submitTaskBuilder);

        Protocol.ServerResponse response = sendRequest(builder);

        if (response.getSubmitResponse().getStatus() == Protocol.Status.OK) {
            return response.getSubmitResponse().getSubmittedTaskId();
        } else {
            throw new Exception("Submit response status = " + response.getSubmitResponse().getStatus());
        }
    }

    public long Subscribe(int taskId) throws Exception {
        Protocol.Subscribe.Builder subscribeBuilder = Protocol.Subscribe.newBuilder();
        subscribeBuilder.setTaskId(taskId);

        Protocol.ServerRequest.Builder builder = Protocol.ServerRequest.newBuilder();
        builder.setSubscribe(subscribeBuilder);

        Protocol.ServerResponse response = sendRequest(builder);

        if (response.getSubscribeResponse().getStatus() == Protocol.Status.OK) {
            return response.getSubscribeResponse().getValue();
        } else {
            throw new Exception("Submit response status = " + response.getSubscribeResponse().getStatus());
        }
    }

    public List<Protocol.Task> ListTasks() throws Exception {
        Protocol.ListTasks.Builder listTasksBuilder = Protocol.ListTasks.newBuilder();

        Protocol.ServerRequest.Builder builder = Protocol.ServerRequest.newBuilder();
        builder.setList(listTasksBuilder);

        Protocol.ServerResponse response = sendRequest(builder);
        if (response.getListResponse().getStatus() == Protocol.Status.OK) {
            List<Protocol.ListTasksResponse.TaskDescription> listTasks = response.getListResponse().getTasksList();
            List<Protocol.Task> result = new ArrayList<>();
            for (Protocol.ListTasksResponse.TaskDescription task : listTasks) {
                task.getTaskId();
                task.getClientId();
                result.add(task.getTask());
            }
            return result;
        } else {
            throw new Exception("ListTasks response status = " + response.getListResponse().getStatus());
        }
    }
}
