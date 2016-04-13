package csc.parallel.server;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Andrey Kokorev
 *         Created on 13.04.2016.
 */
public class TaskSolver
{
    private final Object guard = new Object();
    private AtomicInteger maxThreads;
    private AtomicInteger threadId
    public TaskSolver()
    {
        maxThreads = new AtomicInteger(Runtime.getRuntime().availableProcessors());
    }

    public void solveTask(TaskHolder holder)
    {
        new Thread(() -> {

        });
    }
}

