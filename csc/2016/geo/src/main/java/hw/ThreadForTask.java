package hw;

import communication.Protocol;
import communication.Protocol.*;

import java.io.IOException;
import java.net.Socket;
import java.util.Map;

/**
 * Created by Егор on 15.04.2016.
 */
public class ThreadForTask extends Thread {
    private Socket socket;
    private TaskMap taskMap;

    ThreadForTask(Socket socketFromClient, TaskMap manager) {
        socket = socketFromClient;
        taskMap = manager;
        setDaemon(true);
        setPriority(NORM_PRIORITY);
    }

    ServerRequest getServerRequest() throws IOException {
        /*int size = socket.getInputStream().read();
        byte buf[] = new byte[size];
        socket.getInputStream().read(buf);
        return ServerRequest.parseFrom(buf);*/
        return null;
    }

    private void submitHandler(ServerRequest request, ServerResponse.Builder response) throws IOException {
        Task curTask = request.getSubmit().getTask();
        SubmitTaskResponse.Builder submitResponse = SubmitTaskResponse.newBuilder();
        try {
            int taskId = taskMap.CreateNewTask(request.getClientId(), curTask);
            submitResponse.setSubmittedTaskId(taskId).setStatus(Status.OK);
            response.setSubmitResponse(submitResponse);
//            new Thread(new MyTask(request.getClientId(), request.getSubmit().getTask(), taskMap)).start();
            response.build().writeDelimitedTo(socket.getOutputStream());
        } catch (Exception e) {
            response.setSubmitResponse(submitResponse.setStatus(Status.ERROR));
            response.build().writeDelimitedTo(socket.getOutputStream());
        }
    }

    private void subscribeHandler(ServerRequest request, ServerResponse.Builder response) throws IOException, InterruptedException{
        SubscribeResponse.Builder subscribeResponse = SubscribeResponse.newBuilder();
        long result = taskMap.getTaskResult(request.getSubscribe().getTaskId());
        subscribeResponse.setValue(result).setStatus(Status.OK);
        response.setSubscribeResponse(subscribeResponse);
        response.build().writeDelimitedTo(socket.getOutputStream());
    }

    private void listHandler(ServerResponse.Builder response) throws IOException {
        ListTasksResponse.Builder listResponse = ListTasksResponse.newBuilder();
        listResponse.setStatus(Status.OK);
        Map tasks = taskMap.getTasks();
        for (Object i : tasks.keySet()) {
            ListTasksResponse.TaskDescription.Builder builder = ListTasksResponse.TaskDescription.newBuilder();
            Integer id = (Integer) i;
            MyTask task = (MyTask) tasks.get(id);
            builder.setTask(task.getTask()).setTaskId(id).setClientId(task.getClientId());
            if(task.getStatus() == MyTask.Status.FINISH)
                builder.setResult(task.getResult());
            listResponse.addTasks(builder);
        }
        response.setListResponse(listResponse);
        response.build().writeDelimitedTo(socket.getOutputStream());
    }

    @Override
    public void run() {
        try {
            ServerRequest request = ServerRequest.parseDelimitedFrom(socket.getInputStream());
            ServerResponse.Builder response = ServerResponse.newBuilder();
            response.setRequestId(request.getRequestId());
            if (request.hasSubmit()) {
                submitHandler(request, response);
            } else if (request.hasSubscribe()) {
                subscribeHandler(request, response);
            } else if(request.hasList()) {
                listHandler(response);
            }
            socket.close();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
}