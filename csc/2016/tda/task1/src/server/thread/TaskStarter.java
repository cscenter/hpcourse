package server.thread;

import server.storage.Counter;
import server.storage.TaskStorage;
import server.storage.TaskWrapper;

import static communication.Protocol.*;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
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
            threads.add(new Subscriber(socket, request.getRequestId(), storage, request.getSubscribe().getTaskId()));
        }
        if (request.hasSubmit()) {
            int taskId = COUNTER.next();
            Calculator calculator = new Calculator(socket, request.getRequestId(), request.getSubmit().getTask(),
                    storage, COUNTER.next());
            storage.add(taskId, request.getClientId(), calculator);
            threads.add(calculator);
        }
        if (request.hasList()) {
            threads.add(new StatusResponser(socket, request.getRequestId(), storage));
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
