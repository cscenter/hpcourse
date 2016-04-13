package csc.parallel.server;

import communication.Protocol;
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
    private final int id;
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

    public int getId()
    {
        return id;
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
        this.done.set(true);
        this.result = result;
    }

    public TaskHolder(int id, String client_id, Task task)
    {
        this.id = id;
        this.task = task;
        this.client_id = client_id;
    }
}
