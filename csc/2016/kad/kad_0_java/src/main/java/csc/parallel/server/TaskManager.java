package csc.parallel.server;

import communication.Protocol;
import communication.Protocol.ListTasksResponse;
import communication.Protocol.ListTasksResponse.TaskDescription;
import communication.Protocol.Task;
import communication.Protocol.ServerRequest;
import communication.Protocol.ServerResponse;
import communication.Protocol.Subscribe;
import communication.Protocol.SubscribeResponse;
import communication.Protocol.SubmitTaskResponse;
import communication.Protocol.WrapperMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Andrey Kokorev
 *         Created on 13.04.2016.
 */
public class TaskManager implements Runnable
{
    private final Logger logger = LoggerFactory.getLogger(TaskManager.class);
    private TaskSolver taskSolver;
    private ServerSocket socket;
    private final int port;

    private final AtomicInteger taskIDGenerator = new AtomicInteger();
    private final AtomicInteger clientIDGenerator = new AtomicInteger();

    // id -> task
    private final Map<Integer, TaskHolder> tasks = new HashMap<>();

    public TaskManager(int port)
    {
        this.port = port;
        this.taskSolver = new TaskSolver(tasks);
    }

    @Override
    public void run()
    {
        Thread.currentThread().setName("-- Task manager");
        try
        {
            this.socket = new ServerSocket(port);
            this.socket.setSoTimeout(1000);
        } catch (IOException e)
        {
            logger.error(e.getMessage());
            return;
        }
        logger.info("I'm up");

        while (true)
        {
            try
            {
                Socket client = socket.accept();

                int clientId = clientIDGenerator.incrementAndGet();
                String name = "-- Client listener " + clientId;
                new Thread(() -> {
                    try
                    {
                        while(handleClient(client));
                        logger.debug("Connection with {} closed.", clientId);
                    }
                    catch (IOException e)
                    {
                        logger.error("Client listener {} crashed with {}", clientId, e.getMessage());
                    }
                }, name).start();
            } catch (SocketTimeoutException e)
            {
                if(Thread.interrupted())
                    break;
            }
            catch (IOException e)
            {
                logger.error(e.getMessage());
            }
        }
    }

    /**
     *  Handles message from client. Doesn't support multiple requests in one message.
     * @param client
     * @throws IOException
     */
    private boolean handleClient(Socket client) throws IOException
    {
        WrapperMessage w = WrapperMessage.parseDelimitedFrom(client.getInputStream());
        if(w == null)
        {
            return false;
        }

        ServerRequest request = w.getRequest();
        logger.trace("Handle client {}", request.getClientId());
        if(request.hasSubmit())
        {
            handleSubmitTask(request, client.getOutputStream());
            return true;
        }

        if(request.hasList())
        {
            handleListTasks(request, client.getOutputStream());
            return true;
        }

        if(request.hasSubscribe())
        {
            handleSubscription(request, client.getOutputStream());
        }
        return true;
    }

    private void handleSubmitTask(ServerRequest request, OutputStream clientOut)
    {
        Task task = request.getSubmit().getTask();
        int id = taskIDGenerator.incrementAndGet();
        logger.trace("handleSubmitTask (id:{}, client:{})", id, request.getClientId());

        if(!isValid(task))
        {
            logger.debug("Task {} has invalid dependencies", id);

            SubmitTaskResponse.Builder r = SubmitTaskResponse.newBuilder()
                    .setStatus(Protocol.Status.ERROR)
                    .setSubmittedTaskId(id);

            ServerResponse response = ServerResponse.newBuilder()
                    .setRequestId(request.getRequestId())
                    .setSubmitResponse(r)
                    .build();

            try
            {
                wrapAndSend(response, clientOut);
            } catch (IOException e)
            {
                logger.error("Error on SubmitTask response.\n{}", e.getMessage());
            }
            return;
        }

        TaskHolder holder = new TaskHolder(id, request.getClientId(), task);
        synchronized (tasks)
        {
            tasks.put(id, holder);
        }

        SubmitTaskResponse.Builder r = SubmitTaskResponse.newBuilder()
                .setStatus(Protocol.Status.OK)
                .setSubmittedTaskId(id);

        ServerResponse response = ServerResponse.newBuilder()
                .setRequestId(request.getRequestId())
                .setSubmitResponse(r)
                .build();

        try
        {
            wrapAndSend(response, clientOut);
        } catch (IOException e)
        {
            logger.error("Error on SubmitTask response.\n{}", e.getMessage());
        }

        taskSolver.solveTask(holder);
    }

    private boolean isValid(Task task)
    {
        List<Integer> deps = new ArrayList<>();
        if(task.getA().hasDependentTaskId())
            deps.add(task.getA().getDependentTaskId());
        if(task.getB().hasDependentTaskId())
            deps.add(task.getB().getDependentTaskId());
        if(task.getP().hasDependentTaskId())
            deps.add(task.getP().getDependentTaskId());
        if(task.getM().hasDependentTaskId())
            deps.add(task.getM().getDependentTaskId());

        synchronized (tasks)
        {
            // check all dependencies are prepared for execution
            return deps.stream().allMatch(tasks::containsKey);
        }
    }

    private void handleListTasks(ServerRequest request, OutputStream clientOut)
    {
        logger.trace("handleListTasks client:{}", request.getClientId());
        ListTasksResponse.Builder listResponse = ListTasksResponse.newBuilder();

        synchronized (tasks)
        {
            for (TaskHolder h : tasks.values())
            {
                TaskDescription.Builder td = TaskDescription.newBuilder()
                        .setTaskId(h.getTaskId())
                        .setTask(h.getTask());

                if(h.isDone())
                    td = td.setResult(h.getResult());

                listResponse.addTasks(td);
            }
        }

        listResponse.setStatus(Protocol.Status.OK);

        ServerResponse response = ServerResponse.newBuilder()
                .setRequestId(request.getRequestId())
                .setListResponse(listResponse)
                .build();

        try
        {
            wrapAndSend(response, clientOut);
        } catch (IOException e)
        {
            logger.error("Error on listTasks response.\n{}", e.getMessage());
        }
    }

    private void handleSubscription(ServerRequest request, OutputStream clientOut)
    {
        Subscribe subscribe = request.getSubscribe();
        logger.trace("handleSubscription client:{} on task:{}", request.getClientId(), subscribe.getTaskId());

        String name = String.format("-- Subscription (%s, %d)", request.getClientId(), subscribe.getTaskId());
        new Thread(() -> {
            TaskHolder holder;
            synchronized (tasks)
            {
                holder = tasks.get(subscribe.getTaskId());
            }
            synchronized (holder.lock)
            {
                try
                {
                    //waiting for task completion
                    while(!holder.isDone())
                        holder.lock.wait();

                    SubscribeResponse.Builder s = SubscribeResponse.newBuilder()
                            .setValue(holder.getResult())
                            .setStatus(Protocol.Status.OK);

                    ServerResponse response = ServerResponse.newBuilder()
                            .setRequestId(request.getRequestId())
                            .setSubscribeResponse(s)
                            .build();

                    wrapAndSend(response, clientOut);

                } catch (InterruptedException e)
                {
                    logger.error(
                            "Subscription ({}, {}) interrupted",
                            request.getClientId(),
                            subscribe.getTaskId()
                    );
                } catch (IOException e)
                {
                    logger.error("Error on subscription response.\n{}", e.getMessage());
                }
            }
        }, name).start();
    }

    private void wrapAndSend(ServerResponse response, OutputStream out) throws IOException
    {
        WrapperMessage msg = WrapperMessage.newBuilder().setResponse(response).build();
        msg.writeDelimitedTo(out);
    }
}
