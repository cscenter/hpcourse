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
import java.net.Socket;
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
    private ConnectionsManager connectionsManager;
    private TaskSolver solverPool;

    private final AtomicInteger idGenerator = new AtomicInteger();

    // id -> task
    private final Map<Integer, TaskHolder> tasks = new HashMap<>();

    public TaskManager(ConnectionsManager connectionsManager)
    {
        this.connectionsManager = connectionsManager;
        this.solverPool = new TaskSolver(tasks);
    }

    @Override
    public void run()
    {
        List<Socket> clients = connectionsManager.getClients();

        while (true)
        {
            for (Socket client : clients)
            {
                try
                {
                    if(client.getInputStream().available() == 0)
                    {
                        continue;
                    }
                    handleClient(client);
                } catch (IOException e)
                {
                    logger.error(e.getMessage());
                }
            }

        }
    }

    /**
     *  Handles message from client. Doesn't support multiple requests in one message.
     * @param client
     * @throws IOException
     */
    private void handleClient(Socket client) throws IOException
    {
        ServerRequest request = WrapperMessage.parseFrom(client.getInputStream()).getRequest();
        if(request.hasSubmit())
        {
            handleSubmitTask(request, client.getOutputStream());
            return;
        }

        if(request.hasList())
        {
            handleListTasks(request, client.getOutputStream());
            return;
        }

        if(request.hasSubscribe())
        {
            handleSubscription(request, client.getOutputStream());
        }
    }

    private void handleSubmitTask(ServerRequest request, OutputStream clientOut)
    {
        Task task = request.getSubmit().getTask();

        int id = idGenerator.incrementAndGet();

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


        TaskHolder holder = new TaskHolder(id, request.getClientId(), task);
        solverPool.solveTask(holder);
    }

    private void handleListTasks(ServerRequest request, OutputStream clientOut)
    {
        ListTasksResponse.Builder listResponse = ListTasksResponse.newBuilder();

        synchronized (tasks)
        {
            for (TaskHolder h : tasks.values())
            {
                TaskDescription.Builder td = TaskDescription.newBuilder()
                        .setTaskId(h.getId())
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
        String name = String.format("Subscription (%s, %d)", request.getClientId(), subscribe.getTaskId());

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
                    logger.info(
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
        msg.writeTo(out);
    }
}
