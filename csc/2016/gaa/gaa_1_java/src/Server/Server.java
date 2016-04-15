package Server;


import communication.Protocol;
import communication.Protocol.ListTasksResponse.TaskDescription;
import communication.Protocol.ServerRequest;
import communication.Protocol.ServerResponse;
import communication.Protocol.SubscribeResponse;
import communication.Protocol.Task;
import communication.Protocol.Task.Param;
import util.SynchronizedInt;

import java.io.IOException;
import java.lang.reflect.Parameter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by scorpion on 14.04.16.
 */

public class Server extends Thread{

    public Server(int serverPort) throws IOException {
        this.serverPort = serverPort;
        ids = new SynchronizedInt();
        serverSocket = new ServerSocket(serverPort);
    }

    Socket connection;

    @Override
    public void run() {
        try {
            while (true) {
                connection = serverSocket.accept();
                new Thread() {
                    @Override
                    public void run() {
                        try {
                            ServerRequest request = ServerRequest.parseDelimitedFrom(connection.getInputStream());
                            if (request.hasSubmit()) {
                                new TaskHandler(request).start();
                            }
                            if (request.hasSubscribe()) {
                                new SubscribeHandler(request).start();
                            }
                            if (request.hasList()) {
                                new ListHandler(request).start();
                            }

                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }.start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                connection.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public int getServerPort() {
        return serverPort;
    }

    private int serverPort;
    private ServerSocket serverSocket;
    private SynchronizedInt ids;
    private Helper helper;

    public class AbstractHandler extends Thread {

        ServerRequest request;

        AbstractHandler(ServerRequest request) {
            this.request = request;
        }

        protected void sendMessage(ServerResponse message) throws IOException {
            synchronized (connection) {
                message.writeDelimitedTo(connection.getOutputStream());
            }
        }
    }

    public class ListHandler extends AbstractHandler {

        ListHandler(ServerRequest request) {
            super(request);
        }

        @Override
        public void run() {
            Protocol.ListTasksResponse.Builder builder = Protocol.ListTasksResponse.newBuilder().setStatus(Protocol.Status.OK);
            try {
                ArrayList<TaskDescription> tasksDescription = helper.getInstance().getAllTask();
                for (TaskDescription taskDescription : tasksDescription) {
                    builder.addTasks(taskDescription);
                }
            } catch (Exception e) {
                builder.setStatus(Protocol.Status.ERROR);
            }
            try {
                sendMessage((ServerResponse.newBuilder().setRequestId(request.getRequestId()).setListResponse(builder.build()).build()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public class SubscribeHandler extends AbstractHandler {

        SubscribeHandler(ServerRequest request) {
            super(request);
        }

        @Override
        public void run() {
            int taskId = request.getSubscribe().getTaskId();
            TaskDescription description = helper.getInstance().getTaskById(taskId);
            SubscribeResponse.Builder builder = SubscribeResponse.newBuilder().setStatus(Protocol.Status.ERROR);
            try {
                long result;
                Task task = description.getTask();
                synchronized (task) {
                    description = helper.getInstance().getTaskById(taskId);
                    if (description.hasResult())
                        result = description.getResult();
                    else {
                        task.wait();
                        result = helper.getInstance().getTaskById(taskId).getResult();
                    }
                }
                builder.setStatus(Protocol.Status.OK).setValue(result);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            try {
                sendMessage(ServerResponse.newBuilder().setRequestId(request.getRequestId()).setSubscribeResponse(builder.build()).build());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public class TaskHandler extends AbstractHandler {

        private Task task;
        private int task_id;

        TaskHandler(ServerRequest request) {
            super(request);
            task = request.getSubmit().getTask();
            task_id = ids.nextValue();
            helper.getInstance().addTask(task_id, TaskDescription.newBuilder().setTask(task).setClientId(request.getClientId()).
                                            setTaskId(task_id).build());
            new Thread() {
                @Override
                public void run() {
                    try {
                        sendMessage(ServerResponse.newBuilder().setRequestId(request.getRequestId()).setSubmitResponse(
                                Protocol.SubmitTaskResponse.newBuilder().setStatus(Protocol.Status.OK).setSubmittedTaskId(task_id).build()
                        ).build());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }.start();
        }

        private long evaluate(long a, long b, long p, long m, long n) {
            while (n --> 0) {
                b = (a * p + b) % m;
                a = b;
            }
            return a;
        }

        void evaluateParam(int id) throws InterruptedException {
            TaskDescription td = helper.getInstance().getTaskById(id);
            Task task = td.getTask();
            synchronized (task) {
                td = helper.getInstance().getTaskById(id);
                if (!td.hasResult()) {
                    task.wait();
                }
            }
        }

        long calculate(Task task, int task_id) throws InterruptedException {

            if (task.getA().hasDependentTaskId()) {
                evaluateParam(task.getA().getDependentTaskId());
            }

            if (task.getB().hasDependentTaskId()) {
                evaluateParam(task.getB().getDependentTaskId());
            }

            if (task.getM().hasDependentTaskId()) {
                evaluateParam(task.getM().getDependentTaskId());
            }

            if (task.getP().hasDependentTaskId()) {
                evaluateParam(task.getP().getDependentTaskId());
            }

            task = helper.getInstance().getTaskById(task_id).getTask();

            long a = task.getA().getValue();
            long p = task.getP().getValue();
            long m = task.getM().getValue();
            long b = task.getB().getValue();
            long n = task.getN();

            return evaluate(a, b, p, m, n);
        }

        @Override
        public void run() {
            synchronized (task) {
                try {
                    long result = calculate(task, task_id);
                    helper.getInstance().addTask(task_id, TaskDescription.newBuilder().setClientId(request.getClientId()).setTaskId(task_id)
                    .setTask(task).setResult(result).build());
                    task.notifyAll();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
