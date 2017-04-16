package server.thread;

import server.storage.Counter;
import server.storage.TaskStorage;

import static communication.Protocol.*;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Starts all tasks from requests
 * @author Dmitriy Tseyler
 */
public class TaskStarter extends Thread {
    private static final Logger log = Logger.getLogger(TaskStarter.class.getName());

    private static final Counter COUNTER = new Counter();

    private final Socket socket;
    private final ServerRequest request;
    private final TaskStorage storage;

    public TaskStarter(Socket socket, ServerRequest request, TaskStorage storage) {
        this.socket = socket;
        this.request = request;
        this.storage = storage;
    }

    @Override
    public void run() {
        List<AbstractServerThread<?>> threads = new ArrayList<>();
        if (request.hasSubscribe()) {
            threads.add(new Subscriber(socket, request.getRequestId(), request.getClientId(), storage,
                    request.getSubscribe().getTaskId()));
        }
        if (request.hasSubmit()) {
            int taskId = COUNTER.next();
            Calculator calculator = new Calculator(socket, request.getRequestId(), request.getSubmit().getTask(),
                    storage, taskId, request.getClientId());
            storage.add(taskId, calculator);
            threads.add(calculator);
        }
        if (request.hasList()) {
            threads.add(new StatusResponser(socket, request.getRequestId(), request.getClientId(), storage));
        }
        threads.forEach(Thread::start);
        try {
            for (AbstractServerThread<?> thread : threads) {
                thread.join();
            }
            socket.close();
        } catch (InterruptedException | IOException e) {
            log.warning("Error while threads running: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
