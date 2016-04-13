package csc.parallel.server;

import communication.Protocol;
import communication.Protocol.ListTasksResponse;
import communication.Protocol.ListTasksResponse.TaskDescription;
import communication.Protocol.Task;
import communication.Protocol.ServerRequest;
import communication.Protocol.ServerResponse;
import communication.Protocol.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
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
    private TaskSolverPool solverPool;

    private final AtomicInteger idGenerator = new AtomicInteger();

    // id -> task
    private final Map<Integer, TaskHolder> tasks = new HashMap<>();

    public TaskManager(ConnectionsManager connectionsManager, TaskSolverPool solverPool)
    {
        this.connectionsManager = connectionsManager;
        this.solverPool = solverPool;
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

    private void handleClient(Socket client) throws IOException
    {
        ServerRequest request = ServerRequest.parseFrom(client.getInputStream());
        if(request.hasSubmit())
        {
            Task task = request.getSubmit().getTask();

            int id = idGenerator.incrementAndGet();

            TaskHolder holder = new TaskHolder(id, request.getClientId(), task);
            solverPool.solveTask(holder);
        }

        if(request.hasList())
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
                    .setListResponse(listResponse)
                    .build();


            response.writeTo(client.getOutputStream());
        }

        if(request.hasSubscribe())
        {
            Subscribe subscribe = request.getSubscribe();
            String name = String.format("Subscription (%s, %d)", request.getClientId(), subscribe.getTaskId());

            //create thread waiting notification for task completion
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
                        holder.wait();
                    } catch (InterruptedException e)
                    {
                        logger.info(
                            "Subscription ({}, {}) interrupted",
                            request.getClientId(),
                            subscribe.getTaskId()
                        );
                    }
                }
            }, name).start();
        }
    }
}
