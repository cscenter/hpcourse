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
        //System.out.printf("Start new Server\n");


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
                        while (true) {
                            try {
                                ServerRequest request = ServerRequest.parseDelimitedFrom(connection.getInputStream());
                                if (request == null)
                                    break;
                                if (request.hasSubmit()) {
                                    //                           System.out.printf("Get new task\n");
                                    new TaskHandler(request).start();
                                }
                                if (request.hasSubscribe()) {
                                    //          System.out.printf("Subscribe new\n");
                                    new SubscribeHandler(request).start();
                                }
                                if (request.hasList()) {
                                    new ListHandler(request).start();
                                }

                            } catch (IOException e) {
                                break;
                            }
                        }
                        try {
                            connection.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }.start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public int getServerPort() {
        return serverPort;
    }

    private int serverPort;
    private ServerSocket serverSocket;
    private SynchronizedInt ids;    

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
                ArrayList<TaskDescription> tasksDescription = Helper.getInstance().getAllTask();
                for (TaskDescription taskDescription : tasksDescription) {
                    builder.addTasks(taskDescription);
                }
       //         System.out.printf("Successfull List\n");
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
            TaskDescription description = Helper.getInstance().getTaskById(taskId);
            SubscribeResponse.Builder builder = SubscribeResponse.newBuilder().setStatus(Protocol.Status.ERROR);
            try {
                long result;
                if (description != null) {
                    Task task = description.getTask();
                    synchronized (task) {
                        description = Helper.getInstance().getTaskById(taskId);
                        if (description.hasResult())
                            result = description.getResult();
                        else {
                        	while (true) {
                           	   task.wait();
	                            description = Helper.getInstance().getTaskById(taskId); 
	                            if (description.hasResult())
	                            {
	                            	break;
	                            }    	                        
        	                }
        	                result = description.getResult();
                        }
                    }
                    builder.setStatus(Protocol.Status.OK).setValue(result);
         //           System.out.printf("Successfull subscribe\n");
                }
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
            Helper.getInstance().addTask(task_id, TaskDescription.newBuilder().setTask(task).setClientId(request.getClientId()).
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

        long evaluateParam(int id) throws InterruptedException {
            TaskDescription td = Helper.getInstance().getTaskById(id);
            Task task = td.getTask();
            long ret = 0;
            synchronized (task) {
                td = Helper.getInstance().getTaskById(id);
                if (!td.hasResult()) {
                    task.wait();
                    ret = Helper.getInstance().getTaskById(id).getResult();
                } else {
                    ret = td.getResult();
                }
            }
            return ret;
        }

        long calculate(Task task, int task_id) throws InterruptedException {

            long a = task.getA().getValue();
            long p = task.getP().getValue();
            long m = task.getM().getValue();
            long b = task.getB().getValue();
            long n = task.getN();


            if (task.getA().hasDependentTaskId()) {
                a = evaluateParam(task.getA().getDependentTaskId());
            }

            if (task.getB().hasDependentTaskId()) {
                b = evaluateParam(task.getB().getDependentTaskId());
            }

            if (task.getM().hasDependentTaskId()) {
                m = evaluateParam(task.getM().getDependentTaskId());
            }

            if (task.getP().hasDependentTaskId()) {
                p = evaluateParam(task.getP().getDependentTaskId());
            }

            return evaluate(a, b, p, m, n);
        }

        @Override
        public void run() {
            synchronized (task) {
                try {
                    long result = calculate(task, task_id);
                    Helper.getInstance().addTask(task_id, TaskDescription.newBuilder().setClientId(request.getClientId()).setTaskId(task_id)
                            .setTask(task).setResult(result).build());
                    //System.out.printf("Got result - %d  ----- %d\n", task_id, result);
                    task.notifyAll();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
