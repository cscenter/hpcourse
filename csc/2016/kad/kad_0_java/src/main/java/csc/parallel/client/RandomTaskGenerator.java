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
        int k = 1000;
        int l = 2000;
        Random r = new Random();

        for(int i = 0; i < k; i++)
        {
            Client c = new Client(port);
            for(int j = 0; j < l; j++)
            {
                Param a = Param.newBuilder().setValue(r.nextInt(100000)).build();
                Param b = Param.newBuilder().setValue(r.nextInt(100000)).build();
                Param p = Param.newBuilder().setValue(r.nextInt(100000)).build();
                Param m = Param.newBuilder().setValue(1 + r.nextInt(99999)).build();
                long n = 1_000_000_000;
                c.sendTask(a, b, p, m, n);
                System.out.println("Task sent " + (i*k + j));
            }
        }
    }
}
