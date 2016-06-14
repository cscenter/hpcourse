import sync.Semaphore;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static communication.Protocol.*;

/**
 * Created by Pavel Chursin on 10.04.2016.
 */
public class TaskServer {

    final static int PORT = 7979;
    final static AtomicInteger taskID = new AtomicInteger(0);
    final static HashMap<Integer, CalcThread> taskThreads = new HashMap<>();
    final static Semaphore calcThreadsLock = new Semaphore(Runtime.getRuntime().availableProcessors() + 1);

    public static void main(String[] args) {
        try {
            ServerSocket server = new ServerSocket(PORT);

            while (true) {
                new ServerThread(server.accept());
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

class ServerThread extends Thread {

    Socket socket;

    ServerThread(Socket socket) {
        this.socket = socket;
        start();
    }

    @Override
    public void run() {
        while(true) {
            ServerRequest req;
            try {
                WrapperMessage wm = WrapperMessage.parseDelimitedFrom(socket.getInputStream());
                if (wm == null)
                    throw new IOException();
                req = wm.getRequest();
            } catch (IOException e) {
                return;
            }
            new ManagerThread(req, socket);
        }
    }
}

class ManagerThread extends Thread {

    ServerRequest req;
    Socket socket;

    public ManagerThread(ServerRequest req, Socket socket) {
        this.req = req;
        this.socket = socket;
        this.setDaemon(true);
        start();
    }

    @Override
    public void run() {
        if (req.hasSubmit()) {
            registerTask(req);
        }
        if (req.hasSubscribe()) {
            subscribeTask(req);
        }
        if (req.hasList()) {
            listTasks();
        }
    }

    private void listTasks() {
        ListTasksResponse.Builder listBuilder = ListTasksResponse.newBuilder();
        int i = 0;
        Set<Map.Entry<Integer, CalcThread>> entries;
        synchronized (TaskServer.taskThreads) {
            entries = TaskServer.taskThreads.entrySet();
        }
        for (Map.Entry<Integer, CalcThread> entry : entries) {
            ListTasksResponse.TaskDescription.Builder tdBuilder = ListTasksResponse.TaskDescription.newBuilder()
                    .setClientId(entry.getValue().getClientID())
                    .setTaskId(entry.getKey())
                    .setTask(entry.getValue().getTask());
            if (entry.getValue().isReady())
                tdBuilder.setResult(entry.getValue().getResult());
            listBuilder.setTasks(i++, tdBuilder.build());
        }
        ServerResponse sr = ServerResponse.newBuilder()
                .setRequestId(req.getRequestId())
                .setListResponse(listBuilder.build())
                .build();
        sendToClient(WrapperMessage.newBuilder().setResponse(sr).build());
    }

    private void subscribeTask(ServerRequest req) {
        SubscribeResponse.Builder builder = SubscribeResponse.newBuilder();
        int taskID = req.getSubscribe().getTaskId();
        CalcThread thread;
        synchronized (TaskServer.taskThreads) {
            thread = TaskServer.taskThreads.get(taskID);
        }
        if (thread == null) {
            builder.setStatus(Status.ERROR);
        } else {
            builder.setStatus(Status.OK);
            builder.setValue(thread.getResult());
        }
        SubscribeResponse sr = builder.build();
        ServerResponse response = ServerResponse.newBuilder()
                .setSubscribeResponse(sr)
                .setRequestId(req.getRequestId())
                .build();
        sendToClient(WrapperMessage.newBuilder().setResponse(response).build());
    }

    private void registerTask(ServerRequest req) {
        Task task = req.getSubmit().getTask();
        boolean status = true;
        int taskID = TaskServer.taskID.getAndIncrement();
        try {
            synchronized (TaskServer.taskThreads) {
                TaskServer.taskThreads.put(taskID, new CalcThread(task, req.getClientId()));
            }
        } catch (IllegalArgumentException e) {
            status = false;
        }
        WrapperMessage wm = prepareSubmitTaskResponse(req, taskID, status);
        sendToClient(wm);
    }

    private WrapperMessage prepareSubmitTaskResponse(ServerRequest req, int taskID, boolean status) {
        SubmitTaskResponse str = SubmitTaskResponse.newBuilder()
                .setStatus(status ? Status.OK : Status.ERROR)
                .setSubmittedTaskId(taskID)
                .build();
        ServerResponse sr = ServerResponse.newBuilder()
                .setRequestId(req.getRequestId())
                .setSubmitResponse(str)
                .build();
        return WrapperMessage.newBuilder()
                .setResponse(sr)
                .build();
    }

    private void sendToClient(WrapperMessage wm) {
        try {
            wm.writeDelimitedTo(socket.getOutputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

class CalcThread extends Thread {
    long a, b, p, m, n;
    boolean ready = false;
    String clientID;
    Task task;
    final Object monitor = new Object();

    public CalcThread(Task task, String clientId) throws IllegalArgumentException {
        this.task = task;
        this.clientID = clientId;
        a = getValueOrLock(task.getA());
        b = getValueOrLock(task.getB());
        p = getValueOrLock(task.getP());
        m = getValueOrLock(task.getM());
        n = task.getN();
        this.setDaemon(true);
        start();
    }

    @Override
    public void run() {
        TaskServer.calcThreadsLock.lock();
        while (n-- > 0)
        {
            b = (a * p + b) % m;
            a = b;
        }
        //result is a
        ready = true;
        synchronized (monitor) {
            monitor.notifyAll();
        }
        TaskServer.calcThreadsLock.unlock();
    }

    public Task getTask() {
        return task;
    }

    private long getValueOrLock(Task.Param param) throws IllegalArgumentException {
        if (param.hasDependentTaskId()) {
            int id = param.getDependentTaskId();
            CalcThread thread;
            synchronized (TaskServer.taskThreads) {
                thread = TaskServer.taskThreads.get(id);
            }
            if (thread == null)
                throw new IllegalArgumentException("no such taskID: " + id);
            return thread.getResult();
        }
        return param.getValue();
    }

    public String getClientID() {
        return clientID;
    }

    public long getResult() {
        synchronized (monitor) {
            while (!ready)
                try {
                    monitor.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
        }
        return a;
    }

    public boolean isReady() {
        return ready;
    }
}