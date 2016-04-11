package csc.parallel.server;

import communication.Protocol.Task;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Andrey Kokorev
 *         Created on 30.03.2016.
 */
public class Worker implements Runnable
{
    private final Logger logger = LoggerFactory.getLogger(Worker.class);
    private final long id;
    private final Map<Long, Long> resultMap;
    private final Task task;

    public Worker(long id, Task task, Map<Long, Long> resultMap)
    {
        this.id = id;
        this.task = task;
        this.resultMap = resultMap;
    }

    @Override
    public void run()
    {
        logger.info("Worker started for task {}", this.id);
        if(!task.getA().hasValue() || !task.getB().hasValue() ||
           !task.getM().hasValue() || !task.getP().hasValue())
        {
            throw new RuntimeException(String.format("Task %d has unresolved dependencies", id));
        }

        long result = calculate(
                task.getA().getValue(),
                task.getB().getValue(),
                task.getP().getValue(),
                task.getM().getValue(),
                task.getN()
        );

        synchronized (resultMap)
        {
            resultMap.put(id, result);
        }
        logger.info("Task done {}", this.id);
    }

    private long calculate(long a, long b, long p, long m, long n)
    {
        while (n-- > 0)
        {
            b = (a * p + b) % m;
            a = b;
        }
        return a;
    }
}
