package ConcurrentScheduler;

/**
 * Created by Anastasia on 07.05.2015.
 */
public class RecursiveTask extends Task {

    private Scheduler scheduler;
    private int firstSleep = duration/2;
    public RecursiveTask(int id, int newDuration, Scheduler newScheduler){
        super(id, newDuration);
        scheduler = newScheduler;
    }

    @Override
    public void run(){
        try {
            System.out.println(ID + " started");
            Thread.sleep(firstSleep);
            System.out.println(ID + " paused");
        }
        catch (InterruptedException e) {
            System.out.println(ID + " interrupted");
            Thread.currentThread().interrupt();
            return;
        }
        System.out.println("recursive " + -ID + " submitted");
        Task childTask = new Task(-ID, duration);
        ChildTask = childTask;
        scheduler.submit(childTask);
        childTask.future.get();

        try {
            Thread.sleep(duration - firstSleep);
        }
        catch (InterruptedException e) {
            System.out.println(ID + " interrupted");
            Thread.currentThread().interrupt();
        }

    }
}
