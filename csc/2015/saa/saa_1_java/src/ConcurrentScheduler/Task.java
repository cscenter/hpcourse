package ConcurrentScheduler;

/**
 * Created by Anastasia on 03.05.2015.
 */
public class Task implements Runnable {
    protected int ID;
    public Future future;
    protected int duration;
    public Task ChildTask;
    public int sleepLeft;

    public Task(int id, int newDuration)
    {
        ID = id;
        duration = newDuration;
        ChildTask = null;
        sleepLeft = duration;
    }
    @Override
    public void run(){
        try {
            if(ChildTask != null && ChildTask.future.getState().ordinal() < Future.State.Done.ordinal()){
                System.out.println(ID + " resumed");
            } else {
                System.out.println(ID + " started");
            }
            Thread.sleep(duration);
            System.out.println(ID + " done");
        }
        catch (InterruptedException e) {
            System.out.println(ID + " interrupted");
            Thread.currentThread().interrupt();
        }
    }
}
