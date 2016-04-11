package csc.parallel.client;

import communication.Protocol.Task;
import communication.Protocol.Task.Param;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

/**
 * @author Andrey Kokorev
 *         Created on 31.03.2016.
 */
public class Client implements AutoCloseable
{
    private final InetAddress address;
    private final int port;
    private final Socket socket;
    public Client(int port) throws IOException
    {
        this(null, port);
    }

    public Client(String host, int port) throws IOException
    {
        this.port = port;
        this.address = InetAddress.getByName(host);
        this.socket = new Socket(this.address, this.port);
    }


    public void sendTask(Param a, Param b, Param p, Param m, long n) throws IOException
    {
        Task t = Task.newBuilder().setA(a).setB(b).setP(p).setM(m).setN(n).build();
        t.writeTo(socket.getOutputStream());
    }

    @Override
    public void close() throws Exception
    {
        socket.close();
    }
}
