package server;

import communication.Protocol;
import communication.Protocol.*;

import java.net.*;
import java.io.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

class ServerThread extends Thread {
    private static final Logger logger = Logger.getLogger(Server.class.getName());
    private final Socket socket;
    private final TaskManager taskManager;

    private final AtomicInteger activeCounter = new AtomicInteger(0);
    private final Object writeLock = new Object();

    ServerThread(Socket socket, TaskManager taskManager) {
        this.socket = socket;
        this.taskManager = taskManager;
    }

    @Override
    public void run() {
        logger.info("ServerThread " + this.getName() + " started");
        while (true) {
            // Read message and start processing
            try {
                WrapperMessage inputMessage;
                try (InputStream input = socket.getInputStream()) {
                    inputMessage = WrapperMessage.parseDelimitedFrom(input);
                    if (!inputMessage.hasRequest())
                        throw new IOException("Message does not contain request");
                } catch (Exception e) {
                    logger.log(Level.WARNING, this.getName() + " failed to read message", e);
                    e.printStackTrace();
                    break;
                }
                ServerRequest request = inputMessage.getRequest();
                logger.info(this.getName() + " received request " + request.toString());
                processServerRequestAsync(request);
            } catch (Exception e) {
                logger.log(Level.WARNING, this.getName() + "", e);
            }
        }

        // Wait for unfinished tasks
        synchronized (activeCounter) {
            while (activeCounter.get() > 0) {
                try {
                    activeCounter.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        try {
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    /**
     * Calls {@code supplier} in separate thread and updates {@code activeCounter}.
     * Then writes response to {@code socket}
     *
     * @param supplier is supplier of ServerResponse
     */
    private void runAndWriteToSocketAsync(java.util.function.Supplier<ServerResponse.Builder> supplier) {
        logger.info(this.getName() + " start runAndWriteToSocketAsync()");
        activeCounter.incrementAndGet();
        new Thread(() -> {
            WrapperMessage message = WrapperMessage.newBuilder().setResponse(supplier.get()).build();
            synchronized (this.writeLock) {
                try (OutputStream output = socket.getOutputStream()) {
                    message.writeDelimitedTo(output);
                } catch (IOException e) {
                    logger.log(Level.WARNING
                            , this.getName() + " failed to write response, requestId="
                                    + message.getResponse().getRequestId()
                            , e);
                }
            }
            synchronized (activeCounter) {
                activeCounter.decrementAndGet();
                activeCounter.notifyAll();
            }
        }).start();
    }

    /**
     * Process request in separate thread and write response to socket
     */
    private void processServerRequestAsync(ServerRequest request) {
        if (request.hasSubmit()) {
            runAndWriteToSocketAsync(() -> {
                SubmitTaskResponse response = processSubmitTask(request.getSubmit());
                return ServerResponse.newBuilder()
                        .setSubmitResponse(response)
                        .setRequestId(request.getRequestId());
            });
        }
        if (request.hasSubscribe()) {
            runAndWriteToSocketAsync(() -> {
                SubscribeResponse response = processSubscribe(request.getSubscribe());
                return ServerResponse.newBuilder()
                        .setSubscribeResponse(response)
                        .setRequestId(request.getRequestId());
            });
        }
        if (request.hasList()) {
            runAndWriteToSocketAsync(() -> {
                ListTasksResponse response = processListTasks(request.getList(), request.getClientId());
                return ServerResponse.newBuilder()
                        .setListResponse(response)
                        .setRequestId(request.getRequestId());
            });
        }
    }

    private SubmitTaskResponse processSubmitTask(SubmitTask submitTask) {
        Protocol.SubmitTaskResponse.Builder response = Protocol.SubmitTaskResponse.newBuilder();

        Protocol.Task task = submitTask.getTask();
        int id = taskManager.addTask(task);
        response.setSubmittedTaskId(id).setStatus(Protocol.Status.OK);
        return response.build();
    }

    private SubscribeResponse processSubscribe(Subscribe subscribe) {
        Protocol.SubscribeResponse.Builder response = Protocol.SubscribeResponse.newBuilder();

        int id = subscribe.getTaskId();
        try {
            long result = taskManager.getResult(id);
            response.setValue(result);
            response.setStatus(Status.OK);
        } catch (IllegalArgumentException e) {
            response.setStatus(Status.ERROR);
        }

        return response.build();
    }

    private ListTasksResponse processListTasks(ListTasks listTasks, String clientId) {
        Protocol.ListTasksResponse.Builder response = Protocol.ListTasksResponse.newBuilder();
        try {
            for (Integer id : taskManager.getAllTasks()) {
                Protocol.ListTasksResponse.TaskDescription.Builder taskDescBuilder
                        = Protocol.ListTasksResponse.TaskDescription.newBuilder();
                taskDescBuilder.setClientId(clientId)
                        .setTaskId(id)
                        .setTask(taskManager.getTask(id));
                if (taskManager.hasResult(id))
                    taskDescBuilder.setResult(taskManager.getResult(id));

                response.addTasks(taskDescBuilder);
            }
            response.setStatus(Status.OK);
        } catch (Exception e) {
            response.setStatus(Status.ERROR);
        }
        return response.build();
    }
}