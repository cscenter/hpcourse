package csc.parallel.client;

import communication.Protocol.ServerRequest;
import communication.Protocol.SubmitTask;
import communication.Protocol.Task;
import communication.Protocol.Task.Param;
import communication.Protocol.WrapperMessage;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Andrey Kokorev
 *         Created on 31.03.2016.
 */
public class Client implements AutoCloseable
{
    private final Logger logger = LoggerFactory.getLogger(Client.class);
    private final InetAddress address;
    private final int port;
    private final Socket socket;
    private final String clientId;
    private long requestsSent = 0;
    public Client(String clientId, int port) throws IOException
    {
        this(clientId, null, port);
    }

    public Client(String clientId, String host, int port) throws IOException
    {
        this.clientId = clientId;
        this.port = port;
        this.address = InetAddress.getByName(host);
        this.socket = new Socket(this.address, this.port);
    }

    /**
     * Sends task using given params, returns task id
     * @throws IOException
     */
    public int sendTask(Param a, Param b, Param p, Param m, long n) throws IOException
    {
        Task.Builder t = Task.newBuilder().setA(a).setB(b).setP(p).setM(m).setN(n);

        SubmitTask.Builder st = SubmitTask.newBuilder().setTask(t);
        ServerRequest.Builder r = ServerRequest.newBuilder()
                .setClientId(this.clientId)
                .setRequestId(requestsSent++)
                .setSubmit(st);

        WrapperMessage w = WrapperMessage.newBuilder().setRequest(r).build();
        w.writeDelimitedTo(socket.getOutputStream());

        WrapperMessage response = WrapperMessage.parseDelimitedFrom(socket.getInputStream());
        int taskId = response.getResponse().getSubmitResponse().getSubmittedTaskId();

        logger.debug("Task sent {}", taskId);
        return taskId;
    }

    @Override
    public void close()
    {
        try
        {
            socket.close();
        } catch (IOException e)
        {
            logger.error("Socket close error");
            logger.error(e.getMessage());
        }
    }
}
