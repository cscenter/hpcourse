package csc.parallel.server;

/**
 * @author Andrey Kokorev
 *         Created on 13.04.2016.
 */
public class TaskSolverPool
{
    private int maxThreads;
    public TaskSolverPool()
    {
        maxThreads = Runtime.getRuntime().availableProcessors();
    }

    public void solveTask(TaskHolder holder)
    {

    }
}

