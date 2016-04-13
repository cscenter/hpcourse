package csc.parallel.server;

import communication.Protocol;
import communication.Protocol.Task;

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
    private boolean done = false;

    public String getClient_id()
    {
        return client_id;
    }

    public boolean isDone()
    {
        return done;
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
        this.done = true;
        this.result = result;
    }

    public TaskHolder(int id, String client_id, Task task)
    {
        this.id = id;
        this.task = task;
        this.client_id = client_id;
    }
}
