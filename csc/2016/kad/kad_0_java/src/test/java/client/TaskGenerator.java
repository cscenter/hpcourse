package client;

import communication.Protocol;
import communication.Protocol.Task.Param;
import csc.parallel.server.TaskManager;
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
    private static final Logger logger = LoggerFactory.getLogger(TaskGenerator.class);
    private final int port;

    public static void main(String[] args) throws IOException, InterruptedException, ExecutionException
    {
        int port = 5555;
        int tasks = 100;
        int measuresCount = 50;

//        Thread tm = new Thread(new TaskManager(port));
//        tm.start();
//
//        //wait a bit
//        Thread.sleep(100);
//
//        TaskGenerator t = new TaskGenerator(port);
//        t.checkDependencies();
//        t.checkCalculation(measuresCount);
//
//        tm.interrupt();

        for(int i = 1; i <= Runtime.getRuntime().availableProcessors(); i++)
        {
            logger.info("Threads: {}", i);
            Thread tm1 = new Thread(new TaskManager(port, i));
            tm1.start();

            //wait a bit
            Thread.sleep(100);

            TaskGenerator tg = new TaskGenerator(port);

            tg.measureTaskExecution(
                    TaskGenerator.constSupplier(12640, 1165, 16548974, 4578, 100_000),
                    "Random",
                    tasks,
                    measuresCount
            );

            tm1.interrupt();
            //wait a bit
            Thread.sleep(100);
        }
    }

    public TaskGenerator(int port)
    {
        this.port = port;
    }

    public static Supplier<Protocol.Task> constSupplier(long a, long b, long p, long m, long n)
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
        logger.info("Measurement. taskCount:{}, measureCount:{}, client:{}", taskCount, measureCount, clientName);
        List<Future<Long>> subscriptions = new ArrayList<>();
        long time = System.currentTimeMillis();

        for(int i = 0; i < measureCount; i++)
        {
            logger.debug("Measurement {}", i);
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
        logger.info("Done. Total time {} ms, avg {} ms", (time1 - time), result);

        return time1 - time;
    }

    public boolean checkCalculation(int checkTimes) throws IOException, InterruptedException, ExecutionException
    {
        logger.info("Check calculation. checkTimes:{}", checkTimes);
        int maxa = 10000, maxb = 10000, maxp = 10000, maxm = 10000, n = 1_000_000;
        Supplier<Protocol.Task> supplier = TaskGenerator.randomSupplier(maxa, maxb, maxp, maxm, n);

        try(Client c = new Client("Checker", this.port))
        {
            for (int i = 0; i < checkTimes; i++)
            {
                Protocol.Task t = supplier.get();

                long result = compute(t);

                int id = c.sendTask(t);
                long result1 = c.subscribe(id).get();

                if(result != result1)
                {
                    logger.info(
                            "Incorrect on {}, {}, {}, {}, {}",
                            t.getA().getValue(),
                            t.getB().getValue(),
                            t.getP().getValue(),
                            t.getM().getValue(),
                            n);
                    return false;
                }
            }
        }
        logger.info("Everything is correct");
        return true;
    }


    public boolean checkDependencies () throws IOException, InterruptedException, ExecutionException
    {
        logger.info("Check dependencies");
        /*
            t1   t2
              \  /
               t3   t4
                 \  /
                  t5
        */

        Supplier<Protocol.Task> supp = TaskGenerator.randomSupplier(10000, 10000, 10000, 10000, 1_000_000);
        Protocol.Task t1 = supp.get(), t2 = supp.get(), t3 = supp.get(), t4 = supp.get(), t5 = supp.get();

        long r1 = compute(t1), r2 = compute(t2);
        t3 = t3.toBuilder()
                .setA(Param.newBuilder().setValue(r1))
                .setB(Param.newBuilder().setValue(r2))
                .build();

        long r3 = compute(t3);
        long r4 = compute(t4);
        t5 = t5.toBuilder()
                .setA(Param.newBuilder().setValue(r3))
                .setB(Param.newBuilder().setValue(r4))
                .build();

        long result = compute(t5);


        try (Client c = new Client("Tree dependencies", this.port))
        {
            int id1 = c.sendTask(t1);
            int id2 = c.sendTask(t2);
            t3 = t3.toBuilder()
                    .setA(Param.newBuilder().setDependentTaskId(id1))
                    .setB(Param.newBuilder().setDependentTaskId(id2))
                    .build();
            int id3 = c.sendTask(t3);
            int id4 = c.sendTask(t4);
            t5 = t5.toBuilder()
                .setA(Param.newBuilder().setDependentTaskId(id3))
                .setB(Param.newBuilder().setDependentTaskId(id4))
                .build();
            int id5 = c.sendTask(t5);
            long result1 = c.subscribe(id5).get();

            if(result1 != result)
            {
                logger.info("Incorrect result");
                return false;
            }
        }

        logger.info("Correct result");
        return true;
    }

    private long compute(Protocol.Task t)
    {
        long a = t.getA().getValue(),
             b = t.getB().getValue(),
             p = t.getP().getValue(),
             m = t.getM().getValue(),
             n = t.getN();

        while (n-- > 0)
        {
            b = (a * p + b) % m;
            a = b;
        }
        return a;
    }
}
