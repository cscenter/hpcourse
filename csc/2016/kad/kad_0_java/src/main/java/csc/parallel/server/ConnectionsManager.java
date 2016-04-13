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
public class ConnectionsManager implements Runnable
{
    private final Logger logger = LoggerFactory.getLogger(ConnectionsManager.class);
    private int port;
    private ServerSocket socket;
    private final List<Socket> clients = new ArrayList<>();

    public List<Socket> getClients()
    {
        List<Socket> copy;
        synchronized (clients)
        {
            copy = new ArrayList<>(clients);
        }
        return copy;
    }

    public ConnectionsManager(int port) throws IOException
    {
        this.port = port;
    }

    @Override
    public void run()
    {
        try
        {
            socket = new ServerSocket(port);
            listenConnections();
            logger.info("I'm up");
        } catch (IOException e)
        {
            logger.error(e.getMessage());
        }
    }

    private void listenConnections()
    {
        //listening forever
        while(true)
        {
            try
            {
                // wait here for new client
                Socket client = socket.accept();

                //lock only when add to list
                synchronized (clients)
                {
                    clients.add(client);
                }
            } catch (IOException e)
            {
                logger.error(e.getMessage());
            }
        }
    }
}
