package communication.Server;

import communication.Protocol;

import java.io.IOException;
import java.net.Socket;

/**
 * Created by malinovsky239 on 15.04.2016.
 */
public class ServerQueriesHandler implements Runnable {

    private Socket socket;
    private TaskServer server;

    public ServerQueriesHandler(Socket socket, TaskServer server) {
        this.socket = socket;
        this.server = server;
    }

    @Override
    public void run() {
        try {
            Protocol.ServerRequest request = Protocol.ServerRequest.parseDelimitedFrom(this.socket.getInputStream());

            Protocol.ServerResponse.Builder builder = Protocol.ServerResponse.newBuilder();
            builder.setRequestId(request.getRequestId());

            if (request.hasSubmit()) {
                Protocol.Task requestTask = request.getSubmit().getTask();
                int taskId = server.generateTaskId();
                Object monitor = server.generateTaskMonitor(taskId);

                Protocol.ListTasksResponse.TaskDescription.Builder descriptionBuilder = Protocol.ListTasksResponse.TaskDescription.newBuilder();
                descriptionBuilder.setTask(requestTask);
                descriptionBuilder.setClientId(request.getClientId());
                descriptionBuilder.setTaskId(taskId);
                server.addTaskDescription(descriptionBuilder.build());

                synchronized (monitor) {
                    long a = process(requestTask.getA());
                    long b = process(requestTask.getB());
                    long p = process(requestTask.getP());
                    long m = process(requestTask.getM());
                    long n = requestTask.getN();
                    server.setTaskResult(taskId, execute(a, b, p, m, n));
                    monitor.notifyAll();
                }
                Protocol.SubmitTaskResponse.Builder submitTaskResponse = Protocol.SubmitTaskResponse.newBuilder();
                submitTaskResponse.setSubmittedTaskId(taskId);
                submitTaskResponse.setStatus(Protocol.Status.OK);
                builder.setSubmitResponse(submitTaskResponse);
            }
            if (request.hasList()) {
                Protocol.ListTasksResponse.Builder listTasksResponse = Protocol.ListTasksResponse.newBuilder();
                server.getTaskDescriptionsList().forEach(listTasksResponse::addTasks);
                listTasksResponse.setStatus(Protocol.Status.OK);
                builder.setListResponse(listTasksResponse);
            }
            if (request.hasSubscribe()) {
                int taskId = request.getSubscribe().getTaskId();
                Object monitor = server.getTaskMonitor(taskId);
                synchronized (monitor) {
                    if (server.containsTask(taskId)) {
                        while (!server.isFinished(taskId)) {
                            monitor.wait();
                        }
                        long result = server.getTaskResult(taskId);
                        Protocol.SubscribeResponse.Builder subscribeResponse = Protocol.SubscribeResponse.newBuilder();
                        subscribeResponse.setValue(result);
                        subscribeResponse.setStatus(Protocol.Status.OK);
                        builder.setSubscribeResponse(subscribeResponse);
                    } else {
                        throw new Exception("No task with such ID");
                    }
                }
            }
            builder.build().writeDelimitedTo(this.socket.getOutputStream());
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private long process(Protocol.Task.Param value) {
        if (value.hasDependentTaskId()) {
            Object monitor = server.getTaskMonitor(value.getDependentTaskId());
            synchronized (monitor) {
                try {
                    while (!server.isFinished(value.getDependentTaskId())) {
                        monitor.wait();
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        return value.getValue();
    }

    private long execute(long a, long b, long p, long m, long n) {
        while (n-- > 0) {
            b = (a * p + b) % m;
            a = b;
        }
        return a;
    }
}
