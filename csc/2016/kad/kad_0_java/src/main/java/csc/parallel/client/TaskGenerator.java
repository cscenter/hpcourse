package csc.parallel.client;

import communication.Protocol;
import communication.Protocol.Task.Param;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Supplier;

/**
 * @author Andrey Kokorev
 *         Created on 31.03.2016.
 */
public class TaskGenerator
{
    private final Logger logger = LoggerFactory.getLogger(TaskGenerator.class);
    private final int port;

    public static void main(String[] args) throws IOException, InterruptedException
    {
        int port = 5555;
        int tasks = 100;
        int measuresCount = 50;
        TaskGenerator t = new TaskGenerator(port);
//        t.measureTaskExecution(
//                TaskGenerator.randomSupplier(10000, 10000, 10000, 10000, 10_000_000),
//                "Random",
//                tasks
//        );

        t.measureTaskExecution(
                TaskGenerator.randomSupplier(12640, 1165, 16548974, 4578, 1_000_000),
                "Random",
                tasks,
                measuresCount
        );
    }

    public TaskGenerator(int port)
    {
        this.port = port;
    }

    public static Supplier<Protocol.Task> constSupplier(int a, int b, int p, int m, long n)
    {
        return () -> {
            Param pa = Param.newBuilder().setValue(a).build();
            Param pb = Param.newBuilder().setValue(b).build();
            Param pp = Param.newBuilder().setValue(p).build();
            Param pm = Param.newBuilder().setValue(m).build();

            Protocol.Task t = Protocol.Task.newBuilder()
                    .setA(pa).setB(pb).setP(pp).setM(pm).setN(n)
                    .build();
            return t;
        };
    }

    public static Supplier<Protocol.Task> randomSupplier(int maxa, int maxb, int maxp, int maxm, long n)
    {
        Random r = new Random();
        return () -> {
            Param a = Param.newBuilder().setValue(r.nextInt(maxa)).build();
            Param b = Param.newBuilder().setValue(r.nextInt(maxb)).build();
            Param p = Param.newBuilder().setValue(r.nextInt(maxp)).build();
            Param m = Param.newBuilder().setValue(1 + r.nextInt(maxm)).build();

            Protocol.Task t = Protocol.Task.newBuilder()
                    .setA(a).setB(b).setP(p).setM(m).setN(n)
                    .build();
            return t;
        };
    }

    public long measureTaskExecution(Supplier<Protocol.Task> supplier,
                                     String clientName, int taskCount, int measureCount)
            throws IOException, InterruptedException
    {
        List<Future<Long>> subscriptions = new ArrayList<>();
        long time = System.currentTimeMillis();

        for(int i = 0; i < measureCount; i++)
        {
            Client c = new Client(clientName, this.port);

            List<Integer> taskIds = new ArrayList<>();
            for (int j = 0; j < taskCount; j++)
            {
                try
                {
                    Integer taskId = c.sendTask(supplier.get());
                    taskIds.add(taskId);
                } catch (IOException e)
                {
                    logger.error("Task {} not sent", j);
                }
            }

            taskIds.forEach((id) -> subscriptions.add(c.subscribe(id)));

            subscriptions.forEach((f) -> {
                try
                {
                    f.get();
                } catch (InterruptedException | ExecutionException e)
                {
                    logger.error(e.getMessage());
                }
            });

            c.close();
        }
        long time1 = System.currentTimeMillis();
        double result = (time1 - time) / measureCount;
        logger.info("client:{}, Total time {} ms, avg {} ms", clientName, (time1 - time), result);

        return time1 - time;
    }
}
