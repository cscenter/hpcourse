package first;


import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static first.Protocol.ListTasksResponse;
import static first.Protocol.ListTasksResponse.TaskDescription;
import static first.Protocol.ServerRequest;
import static first.Protocol.ServerResponse;
import static first.Protocol.Status;
import static first.Protocol.SubmitTaskResponse;
import static first.Protocol.SubscribeResponse;
import static first.Protocol.Task;


public class Server {
    
    private ServerSocket serverSocket;
    private AtomicInteger nextTaskId;
    private BlockedMap<Integer, TaskThread> tasks;
    
    Server(String host, int port) throws UnknownHostException, IOException {
        nextTaskId = new AtomicInteger(0);
        tasks = new BlockedMap<Integer, TaskThread>();
        serverSocket = new ServerSocket(port, 0, InetAddress.getByName(host));        
    }
    
    void run() throws IOException {
        while (true) {
            Thread t = new Thread(new ServerRunnable(serverSocket.accept()));
            t.start();
        }
    }
    
    
    public static void main(String[] args) throws UnknownHostException, IOException {
        int port = 0;
        String host;
        try {
            port = Integer.parseInt(args[0]);
        } catch(NumberFormatException | IndexOutOfBoundsException e) {
            System.out.println("First argument must be number of port");
            System.exit(0);
        }
        if (args.length >= 2) {
            host = args[1];
        } else {
            host = "localhost";
        }
        (new Server(host, port)).run();
    }


    class ServerRunnable implements Runnable {

        private Socket socket;
        private Set<Thread> socketThreads;
        
        ServerRunnable(Socket socket) {
            this.socket = socket;
            socketThreads = new HashSet<Thread>();
        }

        public void run() {
            ServerRequest request;
            Thread newThread = null;
            while ((request = readRequest()) != null) {
                    if (request.hasSubmit()) {
                        newThread = new TaskThread(socket, request);
                    } else if (request.hasSubscribe()) {
                        newThread = new SubscribeThread(socket, request);
                    } else if (request.hasList()) {
                        newThread = new ListThread(socket, request);
                    }
                    newThread.start();
                    socketThreads.add(newThread);
                    System.out.println("Request get");
            }
            try {
                for (Thread thread : socketThreads) {
                    thread.join();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            try {
                socket.close();
            } catch (IOException e) {
                System.out.println("Cannot close socket");
            }
        }
    
        private ServerRequest readRequest() {
            // Все сообщения длинной менее 255
            // , поэтому размер size - один байт
            ServerRequest res = null;
            try {
                int size = (int) socket.getInputStream().read();
                if (size >= 0) {
                    byte[] data = new byte[size];
                    socket.getInputStream().read(data);
                    res = ServerRequest.parseFrom(data);
                }
            } catch (IOException e) {
                System.out.println("Cannot read request");
            }
            return res;
        }
    }

    class RootThread extends Thread {
        
        protected Socket socket;
        protected ServerRequest request;
        
        RootThread(Socket socket, ServerRequest request) {
            this.socket = socket;
            this.request = request;
        }

        protected void writeResponse(ServerResponse response) {
            try {
                socket.getOutputStream().write(response.getSerializedSize());
                response.writeTo(socket.getOutputStream()); 
                System.out.println("Response send");
            } catch (IOException e) {
                System.out.println("Cannot write ServerResponse");
                e.printStackTrace();
            }
        }        
    }
    
    class TaskThread extends RootThread {
  
        private int taskId;
        private AtomicReference<Status> status;
        private long a, b, p, m, n;
        private AtomicLong result;
        private boolean calculated = false;
        private AtomicReference<TaskDescription> taskDescription;
        
        TaskThread(Socket socket, ServerRequest request) {
            super(socket, request);
            status = new AtomicReference<Status>();
            taskDescription = new AtomicReference<TaskDescription>();
            result = new AtomicLong();
        }
        
        public void run() {
            register();
            setTaskDescription();
            setParameters();
            writeResponse(makeResponse());
            if (status.get() == Status.OK) {
                result.set(calculate());
                calculated = true;
                setTaskDescription();
            }
        }
        
        private void setTaskDescription() {
            TaskDescription.Builder tdb = TaskDescription.newBuilder()
                                            .setTaskId(taskId)
                                            .setClientId(request.getClientId())
                                            .setTask(request.getSubmit().getTask());
            if (calculated) {
                tdb.setResult(result.get());
            }
            taskDescription.set(tdb.build());
        }
        
        private long calculate() {
            while (n-- > 0) {
                b = (a * p + b) % m;
                a = b;
            }
            return a;
        }
        
        private void register() {
            taskId = nextTaskId.incrementAndGet();
            tasks.put(taskId, this);
        }
        
        private void setParameters() {
            status.set(Status.OK);
            Task task = request.getSubmit().getTask();
            a = getParam(task.getA());
            b = getParam(task.getB());
            p = getParam(task.getP());
            m = getParam(task.getM());
            n = task.getN();
        }
        
        private long getParam(Task.Param x) {
            if (!x.hasValue()) {
                try {
                    int dependent = x.getDependentTaskId();
                    if (tasks.get(dependent).getStatus() == Status.ERROR) {
                        status.set(Status.ERROR);
                        return 0;
                    } else {
                        tasks.get(dependent).join();
                    }
                    return tasks.get(dependent).getResult();
                } catch (InterruptedException e) {
                    status.set(Status.ERROR);
                    return 0;
                }
            }
            return x.getValue();
        }
        
        public Status getStatus() {
            return status.get();
        }
        
        public long getResult() {
            return result.get();
        }

        public TaskDescription getTaskDescription() {
            return taskDescription.get();
        }
        
        private ServerResponse makeResponse() {
            SubmitTaskResponse str = SubmitTaskResponse.newBuilder()
                                        .setSubmittedTaskId(taskId)
                                        .setStatus(status.get())
                                        .build();
            return ServerResponse.newBuilder()
                .setRequestId(request.getRequestId())
                .setSubmitResponse(str)
                .build();
        }        
    }
    
    class SubscribeThread extends RootThread {

        SubscribeThread(Socket socket, ServerRequest request) {
            super(socket, request);
        }
        
        public void run() {
            int taskId = request.getSubscribe().getTaskId();
            TaskThread task = tasks.get(taskId);
            if (task != null) {
                Status status = task.getStatus();
                long result;
                
                if (status == Status.OK) {
                    try {
                        task.join();
                        result = task.getResult();
                    } catch (InterruptedException e) {
                        status = Status.ERROR;
                        result = 0l;
                    }
                } else {
                    result = 0l;
                } 
                SubscribeResponse subscribeResponse = SubscribeResponse.newBuilder()
                                                        .setStatus(status)
                                                        .setValue(result)
                                                        .build();
                ServerResponse response = ServerResponse.newBuilder()
                                            .setRequestId(request.getRequestId())
                                            .setSubscribeResponse(subscribeResponse)
                                            .build();
                writeResponse(response);
            } else {
                SubscribeResponse subscribeResponse = SubscribeResponse.newBuilder()
                                                        .setStatus(Status.ERROR)
                                                        .setValue(0)
                                                        .build();
                ServerResponse response = ServerResponse.newBuilder()
                                            .setRequestId(request.getRequestId())
                                            .setSubscribeResponse(subscribeResponse)
                                            .build();
                writeResponse(response);
                System.out.println("Task " + taskId + " doesn't exist");
            }
        }
    }

    class ListThread extends RootThread {
    
        ListThread(Socket socket, ServerRequest request) {
            super(socket, request);
        }
    
        public void run() {
            List<TaskDescription> taskList = new ArrayList<TaskDescription>();
            for (int taskId : tasks.keySetIterable()) {
                TaskDescription taskDescription = tasks.get(taskId).getTaskDescription();
                if (taskDescription != null) {
                    taskList.add(taskDescription);
                }
            }
            ListTasksResponse ltr = ListTasksResponse.newBuilder()
                                    .setStatus(Status.OK)
                                    .addAllTasks(taskList)
                                    .build();
            
            ServerResponse response = ServerResponse.newBuilder()
                                        .setRequestId(request.getRequestId())
                                        .setListResponse(ltr)
                                        .build();
            writeResponse(response);
        }
    }
}