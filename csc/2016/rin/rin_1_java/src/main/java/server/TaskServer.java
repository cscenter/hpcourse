package server;

import communication.Protocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * @author Ivan Rudakov
 */

public class TaskServer {

    private final Logger log = LoggerFactory.getLogger(TaskServer.class);

    private final TaskManager taskManager = new TaskManager();

    private boolean isRunning = false;

    public void run(final int port) {
        log.info("Run on port {}", port);
        isRunning = true;
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (isRunning) {
                new ServeClientThread(serverSocket.accept()).start();
            }
        } catch (IOException e) {
            log.error("Error while running", e);
        }
    }

    public void stop() {
        isRunning = false;
    }

    public static void main(String[] args) {
        new TaskServer().run(Integer.parseInt(args[0]));
    }

    private class ServeClientThread extends Thread {
        private final Socket socket;

        private ServeClientThread(final Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (InputStream in = socket.getInputStream();
                 OutputStream out = socket.getOutputStream()) {

                Protocol.ServerRequest request = Protocol.ServerRequest.parseDelimitedFrom(in);
                log.info("Got request {}", request);

                if (request.hasList()) {
                    Protocol.ListTasksResponse response = Protocol.ListTasksResponse.newBuilder()
                            .setStatus(Protocol.Status.OK)
                            .addAllTasks(taskManager.getTasks())
                            .build();
                    response.writeDelimitedTo(out);
                    out.flush();
                    return;
                }

                if (request.hasSubmit()) {
                    Protocol.Task task = request.getSubmit().getTask();

                    Protocol.SubmitTaskResponse response = Protocol.SubmitTaskResponse.newBuilder()
                            .setStatus(Protocol.Status.OK)
                            .setSubmittedTaskId(taskManager.submit(request.getClientId(), task))
                            .build();
                    response.writeDelimitedTo(out);
                    out.flush();
                    return;
                }

                if (request.hasSubscribe()) {
                    Protocol.SubscribeResponse response = Protocol.SubscribeResponse.newBuilder()
                            .setStatus(Protocol.Status.OK)
                            .setValue(taskManager.subscribe(request.getSubscribe().getTaskId()))
                            .build();
                    response.writeDelimitedTo(out);
                    out.flush();
                }
            } catch (IOException e) {
                log.error("Error while serving client", e);
            }
        }
    }
}
