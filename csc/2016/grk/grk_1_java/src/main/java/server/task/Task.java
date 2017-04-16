package server.task;

public class Task implements Runnable {
    public int id;
    public volatile boolean isDone;
    public volatile long result;
    public Parameter a;
    public Parameter b;
    public Parameter p;
    public Parameter m;
    public Parameter n;
    public String clientId;


    public Task(Parameter a, Parameter b, Parameter p, Parameter m, Parameter n, int id, String clientId) {
        this.a = a;
        this.b = b;
        this.p = p;
        this.m = m;
        this.n = n;
        this.id = id;
        this.clientId = clientId;
    }

    @Override
    public void run() {

        if (!sweepResults(a, b, p, m, n)) return;
        long result = doSomethingUseful(a.value, b.value, p.value, m.value, n.value);

        synchronized (this) {
            isDone = true;
            this.result = result;
        }
    }

    /** varargs helps to save lines of code */
    private boolean sweepResults(Parameter... params) {
        for (Parameter p : params) {
            if (!p.isReady()) {
                Task originTask = p.task;
                synchronized (this) {
                    while (!originTask.isDone) {
                        try {
                            originTask.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    /** most useful method */
    private long doSomethingUseful(long a, long b, long p, long m, long n) {
        while (n-- > 0) { /** arrow operator */
            b = (a * p + b) % m;
            a = b;
        }
        return a;
    }

}