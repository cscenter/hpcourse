package server;

import communication.Protocol;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by Helen on 10.04.2016.
 */
public class Task {

    private Object monitor = new Object();

    private State state = State.PREPARING_DATA;

    private String ClientID;

    private Protocol.Task taskInfo;

    public AtomicLong result;

    public Task(String clientID, Protocol.Task taskInfo) {
        this.ClientID = clientID;
        this.taskInfo = taskInfo;
    }

    public String getClientID() {
        return ClientID;
    }

    public enum State {
        PREPARING_DATA,
        PROCESSING,
        FINISHED
    }

    public Protocol.Task getTaskInfo() {
        return taskInfo;
    }

    public Object getMonitor() {
        return monitor;
    }

    public State getState() {
        return state;
    }


    public void compute(long a, long b, long p, long m, long n)
    {
        synchronized (monitor) {
            state = State.PROCESSING;
            while (n-- > 0) {
                b = (a * p + b) % m;
                a = b;
            }
            result.set(a);
            state = State.FINISHED;
            monitor.notifyAll();
        }
    }

}
