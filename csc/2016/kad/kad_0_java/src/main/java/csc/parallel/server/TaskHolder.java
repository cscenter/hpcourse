package csc.parallel.server;

import communication.Protocol.Task;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Andrey Kokorev
 *         Created on 13.04.2016.
 */
public class TaskHolder
{
    public final Object lock = new Object();
    private final Task task;
    private final int taskId;
    private long result;
    private String client_id;
    private AtomicBoolean done = new AtomicBoolean(false);

    public String getClient_id()
    {
        return client_id;
    }

    public boolean isDone()
    {
        return done.get();
    }

    public int getTaskId()
    {
        return taskId;
    }

    public long getResult()
    {
        return result;
    }

    public Task getTask()
    {
        return task;
    }

    public void setResult(long result)
    {
        this.result = result;
        this.done.set(true);
    }

    public TaskHolder(int taskId, String client_id, Task task)
    {
        this.taskId = taskId;
        this.task = task;
        this.client_id = client_id;
    }
}
