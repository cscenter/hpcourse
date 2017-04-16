package org.compscicenter.kozmirchuk.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;


public class ClientHandler implements Runnable {


    private static final Solver solver = new Solver(4);

    private final Logger logger = LoggerFactory.getLogger(ClientHandler.class);
    private final Object sendLock = new Object();

    private AtomicInteger counter = new AtomicInteger();
    private final Socket client;


    public ClientHandler(Socket client) {
        this.client = client;
    }

    public void run() {

        while (true) {
            try {
                Protocol.WrapperMessage wrapperMessage = Protocol.WrapperMessage.parseDelimitedFrom(client.getInputStream());

                if (wrapperMessage == null) continue;

                Protocol.ServerRequest request = wrapperMessage.getRequest();


                if (request.hasSubmit())
                    send(handleSubmit(request), client.getOutputStream());
                if (request.hasList())
                    send(handleList(request), client.getOutputStream());

                if (request.hasSubscribe())
                    handleSubscribe(request, client.getOutputStream());

            } catch (IOException e) {
                e.printStackTrace();
                logger.error(e.getMessage());
                break;
            }
        }
    }


    private Protocol.ServerResponse handleSubmit(Protocol.ServerRequest request) throws IOException {
        Protocol.Task task = request.getSubmit().getTask();
        int taskId = counter.incrementAndGet();

        Protocol.Status status = solver.push(new TaskWrapper(taskId, task, request.getClientId())) ? Protocol.Status.OK :
                Protocol.Status.ERROR;

        Protocol.SubmitTaskResponse taskResponse = Protocol.SubmitTaskResponse.newBuilder()
                .setStatus(status).setSubmittedTaskId(taskId).build();

        return Protocol.ServerResponse.newBuilder().setSubmitResponse(taskResponse)
                .setRequestId(request.getRequestId()).build();

    }

    private Protocol.ServerResponse handleList(Protocol.ServerRequest request) throws IOException {
        Protocol.ListTasksResponse.Builder listTasksResponse = Protocol.ListTasksResponse.newBuilder();

        List<TaskWrapper> finishedTasks = solver.getFinishedTasks();

        for (TaskWrapper tw : finishedTasks)
            listTasksResponse.addTasks(Protocol.ListTasksResponse.TaskDescription.newBuilder().
                    setClientId(tw.clientId).setTask(tw.task).setResult(tw.getResult()).build());

        listTasksResponse.setStatus(Protocol.Status.OK);

        return Protocol.ServerResponse.newBuilder()
                .setRequestId(request.getRequestId())
                .setListResponse(listTasksResponse)
                .build();

    }

    private void handleSubscribe(Protocol.ServerRequest request, OutputStream out) {
        new Thread(() -> {
            try {
                TaskWrapper taskWrapper = solver.getTask(request.getSubscribe().getTaskId());

                synchronized (taskWrapper.readyLock) {
                    while (!taskWrapper.isDone()) {
                        taskWrapper.readyLock.wait();
                    }
                }

                Protocol.SubscribeResponse.Builder subscribeResponse = Protocol.SubscribeResponse.newBuilder()
                        .setStatus(Protocol.Status.OK).setValue(taskWrapper.getResult());

                send(Protocol.ServerResponse.newBuilder().setRequestId(request.getRequestId())
                        .setSubscribeResponse(subscribeResponse).build(), out);

            } catch (InterruptedException | IOException e) {
                logger.error(e.getMessage());

            }

        }, "Subscriber").start();
    }

    private void send(Protocol.ServerResponse response, OutputStream out) throws IOException {
        Protocol.WrapperMessage message = Protocol.WrapperMessage.newBuilder().setResponse(response).build();
        synchronized (sendLock) {
            message.writeDelimitedTo(out);
        }
    }

}
