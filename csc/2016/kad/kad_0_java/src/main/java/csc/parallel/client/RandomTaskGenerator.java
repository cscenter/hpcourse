package csc.parallel.client;

import communication.Protocol;
import communication.Protocol.Task.Param;
import javafx.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * @author Andrey Kokorev
 *         Created on 31.03.2016.
 */
public class RandomTaskGenerator
{
    private final Logger logger = LoggerFactory.getLogger(RandomTaskGenerator.class);
    public static void main(String[] args) throws IOException, InterruptedException
    {
        int port = 5555;
        int tasks = 100;
        new RandomTaskGenerator().generateAndWait("Random", port, tasks);
    }

    public long generateAndWait(String clientName, int port, int tasks) throws IOException, InterruptedException
    {
        List<Future<Long>> subscriptions = new ArrayList<>();
        Random r = new Random();

        long time = System.currentTimeMillis();

        Client c = new Client(clientName, port);

        List<Integer> taskIds = new ArrayList<>();
        for (int j = 0; j < tasks; j++)
        {
            Param a = Param.newBuilder().setValue(r.nextInt(100000)).build();
            Param b = Param.newBuilder().setValue(r.nextInt(100000)).build();
            Param p = Param.newBuilder().setValue(r.nextInt(100000)).build();
            Param m = Param.newBuilder().setValue(1 + r.nextInt(99999)).build();
            long n = 10_000_000;
            try
            {
                Integer taskId = c.sendTask(a, b, p, m, n);
                taskIds.add(taskId);
            }
            catch (IOException e)
            {
                logger.error("Task {} not sent", j);
            }
        }

        logger.debug("Subscribing, client:{}", clientName);
        taskIds.forEach((id) -> subscriptions.add(c.subscribe(id)));
        logger.info("All tasks sent and subscribed, client:{}", clientName);

        subscriptions.forEach((f) -> {
            try{
                f.get();
            } catch (InterruptedException | ExecutionException e)
            {
                logger.error(e.getMessage());
            }
        });

        long time1 = System.currentTimeMillis();
        logger.info("client:{}, Total time {} ms", clientName, time1 - time);

        c.close();
        return time1 - time;
    }
}
