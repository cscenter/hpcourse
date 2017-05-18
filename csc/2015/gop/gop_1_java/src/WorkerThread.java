public class WorkerThread extends Thread {
    private final SimpleFixedThreadPool pool;

    public WorkerThread(SimpleFixedThreadPool pool) {
        this.pool = pool;
    }

    @Override
    public void run() {
        Runnable target = null;
        while (true) {
            while (target == null) {
                try {
                    target = pool.getTask();
                } catch (InterruptedException e) {
                    ;
                }
            }
            target.run();
            target = null;
        }
    }
}
