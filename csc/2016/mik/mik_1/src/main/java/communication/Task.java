package communication;

/**
 * Created by malinovsky239 on 15.04.2016.
 */
public class Task {
    private Protocol.Task.Param a;
    private Protocol.Task.Param b;
    private Protocol.Task.Param p;
    private Protocol.Task.Param m;
    private long n;

    public Task(Protocol.Task.Param a, Protocol.Task.Param b, Protocol.Task.Param p, Protocol.Task.Param m, long n) {
        this.a = a;
        this.b = b;
        this.p = p;
        this.m = m;
        this.n = n;
    }

    public Protocol.Task.Builder getBuilder() {
        Protocol.Task.Builder builder = Protocol.Task.newBuilder();
        builder.setA(a);
        builder.setB(b);
        builder.setP(p);
        builder.setM(m);
        builder.setN(n);
        return builder;
    }
}
