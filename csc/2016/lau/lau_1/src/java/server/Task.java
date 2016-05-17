package server;

import java.util.Arrays;

public class Task {
    public enum Status {
        RUNNING,
        FINISHED
    }

    int id;
    long a, b, p, m;
    TaskParam[] params = new TaskParam[4];
    long n;
    long result;
    String clientId;

    Status status;

    Task(int id, String clientId, TaskParam a, TaskParam b, TaskParam p, TaskParam m, long n) {
        this.id = id;
        this.clientId = clientId;
        // TODO: code duplicate
        this.a = a.value;
        this.b = b.value;
        this.p = p.value;
        this.m = m.value;
        this.n = n;
        params[0] = a;
        params[1] = b;
        params[2] = p;
        params[3] = m;
        status = Status.RUNNING;
        System.out.println("Created task " + toString());
    }

    @Override
    public String toString() {
        return "Task id: " + id + " client id: " + clientId + " state: " + status.toString()
                + " params: " + Arrays.toString(params) +
                " result: " + result;
    }
}