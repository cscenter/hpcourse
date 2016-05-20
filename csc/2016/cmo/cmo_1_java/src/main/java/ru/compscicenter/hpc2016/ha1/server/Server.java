package ru.compscicenter.hpc2016.ha1.server;

import ru.compscicenter.hpc2016.ha1.communication.Protocol;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class Server extends Thread {
    private ServerSocket serverSocket;

    public Server(int port) throws IOException {
        serverSocket = new ServerSocket(port);
        start();
    }

    @Override
    public void run() {
        try {
            while (true) {
                new Thread(new SessionHandler(serverSocket.accept())).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private static class SessionHandler implements Runnable {
        private Socket clientSocket;

        SessionHandler(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        @Override
        public void run() {
            try {
                while (true) {
                    Protocol.ServerRequest serverRequest = getServerRequest(clientSocket);
                    new Thread(new RequestHandler(serverRequest, clientSocket)).start();
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        Protocol.ServerRequest getServerRequest(Socket clientSocket) throws IOException {
            InputStream is = clientSocket.getInputStream();
            return Protocol.ServerRequest.parseDelimitedFrom(is);
        }

        private static class RequestHandler implements Runnable {
            static private AtomicInteger idCounter = new AtomicInteger(0);
            static final private Map<Integer, Protocol.ListTasksResponse.TaskDescription> taskDescriptionMap =
                    Collections.synchronizedMap(new HashMap<>());
            static final private Map<Integer,  Protocol.Status> taskStatusMap =
                    Collections.synchronizedMap(new HashMap<>());

            private final Socket clientSocket;
            private Protocol.ServerRequest serverRequest;

            RequestHandler(Protocol.ServerRequest serverRequest, Socket clientSocket) {
                this.serverRequest = serverRequest;
                this.clientSocket = clientSocket;
            }

            @Override
            public void run() {
                Protocol.ServerResponse.Builder serverResponseBuilder = Protocol.ServerResponse.newBuilder();
                serverResponseBuilder.setRequestId(serverRequest.getRequestId());

                if (serverRequest.hasSubmit()) {
                    Protocol.SubmitTaskResponse submitResponse = handleSubmitRequest();
                    serverResponseBuilder.setSubmitResponse(submitResponse);
                } else if (serverRequest.hasSubscribe()) {
                    Protocol.SubscribeResponse subscribeResponse = handleSubscribeRequest();
                    serverResponseBuilder.setSubscribeResponse(subscribeResponse);
                } else if (serverRequest.hasList()) {
                    Protocol.ListTasksResponse listResponse = handleListRequest();
                    serverResponseBuilder.setListResponse(listResponse);
                }

                try {
                    sendResponse(serverResponseBuilder.build());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            Protocol.SubmitTaskResponse handleSubmitRequest() {
                Protocol.Task task = serverRequest.getSubmit().getTask();
                int taskId = idCounter.incrementAndGet();

                Protocol.ListTasksResponse.TaskDescription.Builder taskDescriptionBuilder =
                        Protocol.ListTasksResponse.TaskDescription.newBuilder()
                                .setTask(task)
                                .setClientId(serverRequest.getClientId())
                                .setTaskId(taskId);
                taskDescriptionMap.put(taskId, taskDescriptionBuilder.build());
                taskStatusMap.put(taskId, Protocol.Status.OK);

                Protocol.SubmitTaskResponse.Builder submitTaskResponseBuilder =
                        Protocol.SubmitTaskResponse.newBuilder();
                submitTaskResponseBuilder.setSubmittedTaskId(taskId);

                new Thread() {
                    @Override
                    public void run() {
                        long a = task.getA().getValue();
                        long b = task.getB().getValue();
                        long p = task.getP().getValue();
                        long m = task.getM().getValue();
                        long n = task.getN();

                        try {
                            if (task.getA().hasDependentTaskId()) {
                                a = getTaskResult(task.getA().getDependentTaskId());
                            }
                            if (task.getB().hasDependentTaskId()) {
                                b = getTaskResult(task.getB().getDependentTaskId());
                            }
                            if (task.getP().hasDependentTaskId()) {
                                p = getTaskResult(task.getP().getDependentTaskId());
                            }
                            if (task.getM().hasDependentTaskId()) {
                                m = getTaskResult(task.getM().getDependentTaskId());
                            }
                            taskDescriptionBuilder.setResult(calculate(a, b, p, m, n));
                        } catch (IllegalArgumentException | ArithmeticException e) {
                            e.printStackTrace();
                            submitTaskResponseBuilder.setStatus(Protocol.Status.ERROR);
                            taskStatusMap.put(taskId, Protocol.Status.ERROR);
                        }

                        taskDescriptionMap.put(taskId, taskDescriptionBuilder.build());

                        synchronized (task) {
                            task.notifyAll();
                        }
                    }
                }.start();

                if (!submitTaskResponseBuilder.hasStatus()) {
                    submitTaskResponseBuilder.setStatus(Protocol.Status.OK);
                }

                return submitTaskResponseBuilder.build();
            }

            Protocol.SubscribeResponse handleSubscribeRequest() {
                Protocol.SubscribeResponse.Builder subscribeResponseBuilder = Protocol.SubscribeResponse.newBuilder();
                int taskId = serverRequest.getSubscribe().getTaskId();

                if (!taskStatusMap.containsKey(taskId) ||
                    taskStatusMap.get(taskId) == Protocol.Status.ERROR) {
                    subscribeResponseBuilder.setStatus(Protocol.Status.ERROR);
                } else {
                    long taskResult = getTaskResult(taskId);
                    subscribeResponseBuilder.setValue(taskResult);
                    subscribeResponseBuilder.setStatus(Protocol.Status.OK);
                }

                return subscribeResponseBuilder.build();
            }

            Protocol.ListTasksResponse handleListRequest() {
                Protocol.ListTasksResponse.Builder listTasksResponseBuilder = Protocol.ListTasksResponse.newBuilder();
                synchronized (taskDescriptionMap) {
                    listTasksResponseBuilder.addAllTasks(taskDescriptionMap.values());
                }
                listTasksResponseBuilder.setStatus(Protocol.Status.OK);
                return listTasksResponseBuilder.build();
            }

            void sendResponse(Protocol.ServerResponse serverResponse) throws IOException {
                synchronized (clientSocket) {
                    serverResponse.writeDelimitedTo(clientSocket.getOutputStream());
                }
            }

            private long getTaskResult(int taskId) {
                if (!taskDescriptionMap.containsKey(taskId)) {
                    throw new IllegalArgumentException();
                }

                Protocol.ListTasksResponse.TaskDescription taskDescription = taskDescriptionMap.get(taskId);

                while (true) {
                    synchronized (taskDescription.getTask()) {
                        taskDescription = taskDescriptionMap.get(taskId);

                        if (taskStatusMap.get(taskId) == Protocol.Status.ERROR ||
                            taskDescription.hasResult()) {
                            break;
                        }

                        try {
                            taskDescription.getTask().wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }

                return taskDescription.getResult();
            }

            private long calculate(long a, long b, long p, long m, long n) {
                while (n-- > 0) {
                    b = (a * p + b) % m;
                    a = b;
                }
                return a;
            }
        }
    }
}
