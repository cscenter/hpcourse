package csc.parallel.server;

import java.io.IOException;

/**
 * @author Andrey Kokorev
 *         Created on 13.04.2016.
 */
public class Server
{
    private ConnectionsManager connectionsManager;
    private TaskManager taskManager;
    private TaskSolverPool solverPool;

    public Server(int port) throws IOException
    {
        connectionsManager = new ConnectionsManager(port);
        solverPool = new TaskSolverPool();
        taskManager = new TaskManager(connectionsManager, solverPool);
    }

    public void start()
    {
        new Thread(connectionsManager).start();
        new Thread(taskManager).start();
    }

}
