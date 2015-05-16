package ConcurrentScheduler;

import java.util.concurrent.ExecutionException;

/**
 * Created by Anastasia on 03.05.2015.
 */


public class Future<T> {

    public enum State{
        Created,
        Running,
        Done,
        Canceled,
        ThrownException
    }
    private int ThreadId;
    private int TaskId;
    private State state;
    public boolean IsCancellationInProgress;
    private Scheduler scheduler;

    public Future(int taskId, Scheduler newScheduler){
        TaskId = taskId;
        state = State.Created;
        scheduler = newScheduler;
    }

    public boolean startRunning(int id){
        if(casState(State.Created, State.Running)) {
            ThreadId = id;
            return true;
        }
        return false;
    }
    public Future<T> getChildFuture(){
        Task thisTask = scheduler.TaskCollection.get(TaskId);
        if(thisTask.ChildTask != null) {
            return thisTask.ChildTask.future;
        }
        return null;
    }
    public void setState(State newState){
        state = newState;
        synchronized (this) {
            notifyAll();
        }
    }
    public boolean casState(State oldState, State newState){
            synchronized (this){
                if(state == oldState){
                    state = newState;
                    notifyAll();
                    return true;
                }
                return false;
            }
    }

    public State getState(){
        return state;
    }

    public void get() throws RuntimeException{
            throw new RuntimeException();
    }

    public void cancel(){
        if(IsCancellationInProgress){
            return;
        }
        synchronized (this){
            IsCancellationInProgress = true;
        }
        synchronized (scheduler.Tasks){
            Task thisTask = scheduler.TaskCollection.get(TaskId);
            setState(State.Canceled);
            if(thisTask.ChildTask != null) {
                thisTask.ChildTask.future.cancel();
            }

            if (state.equals(State.Created)) {
                scheduler.Tasks.remove(thisTask);
                System.out.println(TaskId + " canceled");
            } else {
                scheduler.Threads.get(ThreadId).interrupt();
            }
        }
    }

}
