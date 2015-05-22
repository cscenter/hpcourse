public class SleepingTask implements Runnable {
    private final int sleepTime;

    @Override
    public void run() {
        try {
            Thread.sleep(sleepTime);
        } catch (InterruptedException e) {
            ;
        }
    }

    public SleepingTask(int sleepTime) {
        if (sleepTime <= 0) {
            throw new IllegalArgumentException();
        }
        this.sleepTime = sleepTime;
    }
}
