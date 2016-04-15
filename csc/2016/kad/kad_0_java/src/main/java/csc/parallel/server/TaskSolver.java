package csc.parallel.server;

import communication.Protocol.Task;
import javafx.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * @author Andrey Kokorev
 *         Created on 13.04.2016.
 */
public class TaskSolver
{
    private final Logger logger = LoggerFactory.getLogger(TaskSolver.class);
    private final Object guard = new Object();
    private int maxThreads;
    private AtomicInteger availableThreads;
    private final Map<Integer, TaskHolder> tasks;

    public int getMaxThreads()
    {
        return maxThreads;
    }

    public TaskSolver(Map<Integer, TaskHolder> tasks)
    {
        this.tasks = tasks;
        this.maxThreads = Runtime.getRuntime().availableProcessors();
        this.availableThreads = new AtomicInteger(this.maxThreads);
    }

    public void solveTask(TaskHolder holder)
    {
        logger.info("Preparing task id:{}, client:{}", holder.getTaskId(), holder.getClient_id());
        Task task = holder.getTask();

        String name = String.format("-- Solver, task %d", holder.getTaskId());
        new Thread(() -> {
            logger.info("Started task id:{}, client:{}", holder.getTaskId(), holder.getClient_id());

            // firstly wait for all dependencies
            Map<String, Long> values = null;
            try
            {
                values = getValues(task);
            } catch (InterruptedException e)
            {
                logger.error("Interrupted solving task {} while waiting for dependencies", holder.getTaskId());
            }

            // here we are ready to solve
            int avail = 0;
            synchronized (guard)
            {
                try
                {
                    do
                    {
                        while((avail = availableThreads.get()) == 0)
                            guard.wait();
                        //try to occupy one slot
                    } while (!availableThreads.compareAndSet(avail, avail - 1));
                } catch (InterruptedException e)
                {
                    logger.info("Solver of task {} interrupted", holder.getTaskId());
                    return;
                }
            }

            holder.setResult(compute(
                    values.get("a"),
                    values.get("b"),
                    values.get("p"),
                    values.get("m"),
                    task.getN()
            ));

            notifyAndFree(holder);

            logger.info("Solved task id:{}, client:{}", holder.getTaskId(), holder.getClient_id());
        }, name).start();
    }

    private void notifyAndFree(TaskHolder holder)
    {
        // Notify all dependant tasks and subscribers
        synchronized (holder.lock)
        {
            holder.lock.notifyAll();
        }

        int avail = 0;
        synchronized (guard)
        {
            do
            {
                avail = availableThreads.get();
            } while (!availableThreads.compareAndSet(avail, avail + 1));

            // free one another solver
            guard.notify();
        }
    }

    private Map<String, Long> getValues(Task task) throws InterruptedException
    {
        Map<String, Long> result = new HashMap<>();

        List<Pair<String, Task.Param>> params = new ArrayList<>();
        params.add(new Pair<>("a", task.getA()));
        params.add(new Pair<>("b", task.getB()));
        params.add(new Pair<>("p", task.getP()));
        params.add(new Pair<>("m", task.getM()));

        List<Pair<String, Task.Param>> notAvaliable = new ArrayList<>();

        for(Pair<String, Task.Param> p : params)
        {
            if(p.getValue().hasValue())
                result.put(p.getKey(), p.getValue().getValue());
            else
                notAvaliable.add(p);
        }

        List<Pair<String, TaskHolder>> waitHolders = new ArrayList<>();
        synchronized (tasks)
        {
            waitHolders.addAll(notAvaliable.stream().map(p -> new Pair<>(
                    p.getKey(),
                    tasks.get(p.getValue().getDependentTaskId())
            )).collect(Collectors.toList()));
        }

        for(Pair<String, TaskHolder> p : waitHolders)
        {
            synchronized (p.getValue().lock)
            {
                while (!p.getValue().isDone())
                    p.getValue().lock.wait();
            }
            result.put(p.getKey(), p.getValue().getResult());
        }

        return result;
    }

    private long compute(long a, long b, long p, long m, long n)
    {
        while (n-- > 0)
        {
            b = (a * p + b) % m;
            a = b;
        }
        return a;
    }
}

