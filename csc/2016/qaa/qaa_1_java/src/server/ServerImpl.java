package server;

import communication.Protocol;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by qurbonzoda on 15.04.16.
 */
public class ServerImpl implements Server {

    public final static Server SERVER_INSTANCE = new ServerImpl();
    private final List<ServerTask> submittedTaskList;
    private final List<ServerTask> startedTaskList;

    private ServerImpl() {
        submittedTaskList = new ArrayList<>();
        startedTaskList = new ArrayList<>();
    }

    private static void printUsage() {
        System.err.println("Usage: java ServerImpl <port number>");
    }
    public static void main(String[] args) {
        if (args.length != 1) {
            printUsage();
            System.exit(1);
        }
        int portNumber = Integer.parseInt(args[0]);
        try (ServerSocket serverSocket = new ServerSocket(portNumber)) {
            while (true) {
                try (Socket clientSocket = serverSocket.accept()) {
                    new Thread(new ClientResolver(SERVER_INSTANCE, clientSocket)).start();
                } catch (IOException e) {
                    System.err.println("Error while accepting connection, exception message: " + e.getMessage());
                    break;
                }
            }
        } catch (IOException e) {
            System.err.println("Error while initiating server, exception message: " + e.getMessage());
            printUsage();
            System.exit(1);
        }
    }

    @Override
    public Protocol.SubmitTaskResponse submitTask(Protocol.SubmitTask request, String clientId) {
        ServerTask serverTask;
        int taskId;
        synchronized (submittedTaskList) {
            taskId = submittedTaskList.size();
            serverTask = new ServerTask(request.getTask(), taskId, clientId);
            submittedTaskList.add(serverTask);
        }

        boolean ok = serverTask.start();

        if (ok) {
            synchronized (startedTaskList) {
                startedTaskList.add(serverTask);
            }
        }

        return Protocol.SubmitTaskResponse.newBuilder()
                .setStatus(ok ? Protocol.Status.OK : Protocol.Status.ERROR).setSubmittedTaskId(taskId).build();
    }

    @Override
    public Protocol.SubscribeResponse subscribe(Protocol.Subscribe request) {
        final int taskId = request.getTaskId();
        ServerTask serverTask;
        synchronized (submittedTaskList) {
            serverTask = submittedTaskList.get(taskId);
        }

        boolean ok = serverTask.successful.get();

        Protocol.SubscribeResponse.Builder responseBuilder = Protocol.SubscribeResponse.newBuilder();

        if (ok) {
            responseBuilder.setStatus(Protocol.Status.OK);
            responseBuilder.setValue(serverTask.getResult());
        } else {
            responseBuilder.setStatus(Protocol.Status.ERROR);
        }

        return responseBuilder.build();
    }

    @Override
    public Protocol.ListTasksResponse listTasks(Protocol.ListTasks request) {
        List<Protocol.ListTasksResponse.TaskDescription> taskDescriptionList = new ArrayList<>();
        Protocol.ListTasksResponse.TaskDescription.Builder taskDescriptionBuilder = Protocol.ListTasksResponse.TaskDescription.newBuilder();
        synchronized (startedTaskList) {
            for (ServerTask serverTask : startedTaskList) {
                taskDescriptionBuilder
                        .setTaskId(serverTask.id)
                        .setTask(serverTask.task)
                        .setClientId(serverTask.clientId);
                if (serverTask.finished.get()) {
                    taskDescriptionBuilder.setResult(serverTask.result.get());
                } else {
                    taskDescriptionBuilder.clearResult();
                }
                taskDescriptionList.add(taskDescriptionBuilder.build());
            }
        }
        Protocol.ListTasksResponse.Builder responseBuilder = Protocol.ListTasksResponse.newBuilder().setStatus(Protocol.Status.OK);
        responseBuilder.addAllTasks(taskDescriptionList);

        return responseBuilder.build();
    }

    private class ServerTask {
        private final Protocol.Task task;
        private final int id;
        private final String clientId;
        private final AtomicBoolean finished;
        private final AtomicBoolean successful;
        private final AtomicLong result;

        public ServerTask(Protocol.Task task, int id, String clientId) {
            this.task = task;
            this.id = id;
            this.clientId = clientId;
            finished = new AtomicBoolean(false);
            successful = new AtomicBoolean(true);
            result = new AtomicLong();
        }

        public synchronized boolean start() {
            if (finished.get()) {
                return successful.get();
            }

            long a = getValueOf(task.getA());
            long b = getValueOf(task.getB());
            long p = getValueOf(task.getP());
            long m = getValueOf(task.getM());
            long n = task.getN();
            successful.set(successful.get() & (m != 0));

            if (!successful.get()) {
                finished.set(true);
            } else {
                new Thread(new Calculator(this, a, b, p, m, n)).start();
            }

            notifyAll();
            return successful.get();
        }

        private class Calculator implements Runnable {
            final ServerTask serverTask;
            long a, b, p, m, n;

            public Calculator(ServerTask serverTask, long a, long b, long p, long m, long n) {
                this.serverTask = serverTask;
                this.a = a;
                this.b = b;
                this.p = p;
                this.m = m;
                this.n = n;
            }

            @Override
            public void run() {
                synchronized (serverTask) {
                    while (n-- > 0) {
                        b = (a * p + b) % m;
                        a = b;
                    }
                    serverTask.result.set(a);
                    serverTask.finished.set(true);
                    serverTask.notifyAll();
                }
            }
        }

        public synchronized long getResult() {
            while (!finished.get()) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    successful.set(false);
                    break;
                }
            }
            return result.get();
        }

        private long getValueOf(Protocol.Task.Param param) {
            if (param.hasValue()) {
                return param.getValue();
            } else {
                ServerTask dependent;
                synchronized (submittedTaskList) {
                    int dependentId = param.getDependentTaskId();
                    if (dependentId < 0 || dependentId >= submittedTaskList.size()) {
                        successful.set(false);
                        return -1;
                    }
                    dependent = submittedTaskList.get(param.getDependentTaskId());
                }
                long result = dependent.getResult();
                successful.compareAndSet(true, dependent.successful.get());
                return result;
            }
        }
    }
}
