package server;

import communication.Protocol;
import communication.Task;
import util.SynchronizedMap;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;

public class TaskOrganizer {
    private static SynchronizedMap<Integer, Task> tasks = new SynchronizedMap<>();
    private static AtomicInteger taskNumber = new AtomicInteger(0);

    private TaskOrganizer() {
    }

    public static int addTask(Protocol.Task protocolTask, String clientId) {
        int id = taskNumber.incrementAndGet();
        Task task = new Task(protocolTask, id, clientId);
        tasks.put(id, task);
        new Thread(task).start();
        return id;
    }

    static class ListingThread extends BaseTaskThread {
        public ListingThread(Socket socket, Protocol.ServerRequest serverRequest) {
            super(socket, serverRequest);
        }

        @Override
        public void run() {
            Protocol.ListTasksResponse.Builder response = Protocol.ListTasksResponse.newBuilder();
            for (Task task : tasks.getValues()) {
                Protocol.ListTasksResponse.TaskDescription.Builder builder =
                        Protocol.ListTasksResponse.TaskDescription.newBuilder();
                builder.setClientId(task.getClientId());
                builder.setTaskId(task.getId());
                builder.setTask(task.toProtocolTask());
                if (task.isDone()) {
                    builder.setResult(task.getResult());
                }
                response.addTasks(builder.build());
            }
            response.setStatus(Protocol.Status.OK);

            Protocol.ServerResponse.Builder serverResponseBuilder = Protocol.ServerResponse.newBuilder();
            serverResponseBuilder.setListResponse(response.build());
            sendToClient(serverResponseBuilder);
        }
    }

    static class SubmittingThread extends BaseTaskThread {

        public SubmittingThread(Socket socket, Protocol.ServerRequest serverRequest) {
            super(socket, serverRequest);
        }

        @Override
        public void run() {
            Protocol.Task task = request.getSubmit().getTask();
            Protocol.SubmitTaskResponse.Builder builder = Protocol.SubmitTaskResponse.newBuilder();

            try {
                int id = addTask(task, request.getClientId());
                builder.setSubmittedTaskId(id);
                builder.setStatus(Protocol.Status.OK);
            } catch (IllegalArgumentException e) {
                builder.setStatus(Protocol.Status.ERROR);
            }

            Protocol.ServerResponse.Builder serverResponseBuilder = Protocol.ServerResponse.newBuilder();
            serverResponseBuilder.setSubmitResponse(builder.build());
            sendToClient(serverResponseBuilder);
        }
    }

    static class SubscribingThread extends BaseTaskThread {

        public SubscribingThread(Socket socket, Protocol.ServerRequest serverRequest) {
            super(socket, serverRequest);
        }

        @Override
        public void run() {
            Protocol.SubscribeResponse.Builder builder = Protocol.SubscribeResponse.newBuilder();
            int id = request.getSubscribe().getTaskId();
            Task task = tasks.get(id);

            if (task == null) {
                builder.setStatus(Protocol.Status.ERROR);
            } else {
                while (!task.isDone()) {
                    synchronized (task) {
                        try {
                            task.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
                builder.setValue(task.getResult());
                builder.setStatus(Protocol.Status.OK);
            }

            Protocol.ServerResponse.Builder serverResponseBuilder = Protocol.ServerResponse.newBuilder();
            serverResponseBuilder.setSubscribeResponse(builder.build());
            sendToClient(serverResponseBuilder);
        }
    }

    static abstract class BaseTaskThread implements Runnable {
        protected Socket socket;
        protected Protocol.ServerRequest request;

        public BaseTaskThread(Socket socket, Protocol.ServerRequest serverRequest) {
            this.socket = socket;
            this.request = serverRequest;
        }

        protected void sendToClient(Protocol.ServerResponse.Builder serverResponseBuilder) {
            serverResponseBuilder.setRequestId(request.getRequestId());
            try {
                Protocol.ServerResponse serverResponse = serverResponseBuilder.build();
                OutputStream outputStream = socket.getOutputStream();
                synchronized (outputStream) {
                    outputStream.write(serverResponse.getSerializedSize());
                    serverResponse.writeTo(outputStream);
                    outputStream.flush();
                }
            } catch (IOException e) {
                e.printStackTrace();
                try {
                    socket.close();
                } catch (IOException ee) {
                    e.printStackTrace();
                }
            }
        }
    }
}
