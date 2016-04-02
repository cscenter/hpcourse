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

    int id;
    long a, b, p, m;
    long valueA, valueB, valueP, valueM;
    long n;
    long result;
    String clientId;

    Type type;
    Status status;

    Task(int id, Type type, String clientId, long a, long b, long p, long m, long n) {
        this.id = id;
        this.type = type;
        this.clientId = clientId;
        this.a = a;
        this.b = b;
        this.p = p;
        this.m = m;
        this.n = n;
        this.valueA = a;
        this.valueB = b;
        this.valueP = p;
        this.valueM = m;
        status = Status.RUNNING;
        System.out.println("Created task " + toString());
    }

    @Override
    public String toString() {
        return "Task id: " + id + " client id: " + clientId + " state: " + status.toString()
                + " type: " + type.toString() + " params: "
                + a + " " + b + " " + p + " " + m + " " + n + " result: " + result;
    }
}
