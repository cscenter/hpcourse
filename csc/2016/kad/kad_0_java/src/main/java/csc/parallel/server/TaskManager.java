package csc.parallel.server;

import communication.Protocol.Task;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Andrey Kokorev
 *         Created on 30.03.2016.
 */
public class TaskManager implements Runnable
{
    private final Logger logger = LoggerFactory.getLogger(TaskManager.class);
    private long id = 0;
    private int port;
    private ServerSocket socket;
    private final List<Socket> clients = new ArrayList<>();
    private final Queue<Task> taskQueue = new ArrayDeque<>();
    // id -> result
    private final Map<Long, Long> results = new HashMap<>();

    public TaskManager(int port) throws IOException
    {
        this.port = port;
    }

    private void listenConnections()
    {
        //listening forever
        while(true)
        {
            try
            {
                handleClient(socket.accept());
            } catch (IOException e)
            {
                e.printStackTrace();
            }
        }
    }

    private void handleClient(Socket client) throws IOException
    {
        synchronized (clients)
        {
            clients.add(client);
        }

        Task t = Task.parseFrom(client.getInputStream());

        synchronized (taskQueue)
        {
            taskQueue.add(t);
        }
    }

    @Override
    public void run()
    {
        logger.info("I'm up");
        try
        {
            socket = new ServerSocket(port);
            Thread wm = new Thread(new WorkerManager(), "--- Worker manager");
            wm.start();

            listenConnections();
        } catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    private class WorkerManager implements Runnable
    {
        @Override
        public void run()
        {
            logger.info("I'm up");

            Task t = null;
            while(true)
            {
                synchronized (taskQueue)
                {
                    if(!taskQueue.isEmpty())
                    {
                        t = taskQueue.poll();
                    }
                }
                // nothing to do, wait a bit
                if(t == null)
                {
                    try
                    {
                        Thread.sleep(10);
                    } catch (InterruptedException e)
                    {
                        logger.error(e.getMessage());
                    }
                } else
                {
                    //TODO: dependencies!!
                    String name = "--- Worker " + Long.toString(id);
                    new Thread(new Worker(id++, t, results), name).start();
                    t = null;
                }
            }
        }
    }
}
