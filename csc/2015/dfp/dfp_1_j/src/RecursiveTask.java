/**
 * Created by Ôèëèïï on 06.05.2015.
 */
public class RecursiveTask extends Task {

    private static int numberOfRecTask = 665;

    private int milisecondInBegin;
    private ConcurrentScheduler scheduler;
    public RecursiveTask(int miliSeconds, String name, ConcurrentScheduler scheduler) {
        super(miliSeconds, name);
        milisecondInBegin = miliSeconds;
        this.scheduler = scheduler;
        numberOfRecTask++;
        System.out.println("RecursiveIn" + name + Integer.toString(numberOfRecTask));
    }

    @Override
    public void run() {
        while (miliSeconds > milisecondInBegin / 2) {
            miliSeconds -= sleepTime;

            if (Thread.currentThread().interrupted()) {
                return;
            }

            try {
                Thread.sleep(sleepTime);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        try {
            ConcurrentScheduler.Future f = scheduler.addTask(new Task(milisecondInBegin, "RecursiveIn" + name + Integer.toString(numberOfRecTask)), numberOfRecTask);
            f.waitResult();
        }
        catch (ConcurrentScheduler.AlreadyExistIDException e) {

        }

        while (miliSeconds > 0) {
            miliSeconds -= sleepTime;

            if (Thread.currentThread().interrupted()) {
                return;
            }

            try {
                Thread.sleep(sleepTime);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
