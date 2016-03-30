package server;

public class Task {
    public enum Type {
        DEPENDENT,
        INDEPENDENT
    }

    public enum Status {
        RUNNING,
        FINISHED
    }

    long id;
    long a, b, p, m;
    long n;
    long result;

    Type type;
    Status status;

    Task(long id, Type type, long a, long b, long p, long m, long n) {
        this.type = type;
        this.a = a;
        this.b = b;
        this.p = p;
        this.m = m;
        this.n = n;
        this.id = id;
        status = Status.RUNNING;
        System.out.println("Created task " + toString());
    }

    @Override
    public String toString() {
        return "Task id: " + id + " state: " + status.toString() + " type " + type.toString()
                + " params: " + a + " " + b + " " + p + " " + m + " " + n + " result: " + result;
    }
}
