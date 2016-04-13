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

    public Server(int port) throws IOException
    {
        connectionsManager = new ConnectionsManager(port);
        taskManager = new TaskManager(connectionsManager);
    }

    public void start()
    {
        // TODO: interruption
        new Thread(connectionsManager).start();
        new Thread(taskManager).start();
    }

}
