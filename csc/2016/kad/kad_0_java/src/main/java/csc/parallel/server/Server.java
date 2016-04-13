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
    private TaskSolver solverPool;

    public Server(int port) throws IOException
    {
        connectionsManager = new ConnectionsManager(port);
        solverPool = new TaskSolver();
        taskManager = new TaskManager(connectionsManager, solverPool);
    }

    public void start()
    {
        // TODO: interruption
        new Thread(connectionsManager).start();
        new Thread(taskManager).start();
    }

}
