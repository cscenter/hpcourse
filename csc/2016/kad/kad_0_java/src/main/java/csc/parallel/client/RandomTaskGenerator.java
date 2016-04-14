package csc.parallel.client;

import communication.Protocol.Task.Param;

import java.io.IOException;
import java.util.Random;

/**
 * @author Andrey Kokorev
 *         Created on 31.03.2016.
 */
public class RandomTaskGenerator
{
    public static void main(String[] args) throws IOException
    {
        int port = 5555;
        int clientsNum = 10;
        int tasks = 100;
        Random r = new Random();

        for(int i = 0; i < clientsNum; i++)
        {
            try(Client c = new Client("Client_" + i, port))
            {
                for (int j = 0; j < tasks; j++)
                {
                    Param a = Param.newBuilder().setValue(r.nextInt(100000)).build();
                    Param b = Param.newBuilder().setValue(r.nextInt(100000)).build();
                    Param p = Param.newBuilder().setValue(r.nextInt(100000)).build();
                    Param m = Param.newBuilder().setValue(1 + r.nextInt(99999)).build();
                    long n = 1_000_000;
                    c.sendTask(a, b, p, m, n);
                }
            }
        }
    }
}
