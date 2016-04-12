package task;

public class TaskThread extends Thread {
    private final Object monitor = new Object();
    public final int id;
    public final TaskParam a;
    public final TaskParam b;
    public final TaskParam p;
    public final TaskParam m;
    public final long n;

    private volatile long result;

    private volatile boolean isReady = false;

    public TaskThread(int id, TaskParam a, TaskParam b, TaskParam p, TaskParam m, long n) {
        this.id = id;
        this.a = a;
        this.b = b;
        this.p = p;
        this.m = m;
        this.n = n;
    }

    @Override
    public void run() {
        synchronized (monitor) {
            System.out.println(this.getName() + " run started, isReady = " + isReady);

            if (!isReady) {
                long a = this.a.getValue();
                long b = this.b.getValue();
                long p = this.p.getValue();
                long m = this.m.getValue();
                long n = this.n;

                while (n-- > 0) {
                    b = (a * p + b) % m;
                    a = b;
                }
                result = a;
                isReady = true;
            }
            System.out.println(this.getName() + " notifyAll");
            monitor.notifyAll();
        }
    }

    public long getResult() {
        synchronized (monitor) {
            System.out.println(
                    Thread.currentThread().getName()
                            + " enters "
                            + this.getName()
                            + " getResult(), monitor = " + monitor);

            while (!isReady) {
                try {
                    System.out.println(
                            Thread.currentThread().getName() + " waits for " + monitor);
                    monitor.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            System.out.println(Thread.currentThread().getName() + " quits " + this.getName() + " getResult()");
            return result;
        }
    }
}
