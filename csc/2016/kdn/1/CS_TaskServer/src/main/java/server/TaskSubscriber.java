package server;

/**
 * Created by dkorolev on 4/2/2016.
 */
public class TaskSubscriber {
    public final TaskCallable.ParamType paramType;
    public final Integer taskId;
    public final TaskCallable taskCallable;

    public TaskSubscriber(TaskCallable.ParamType paramType, Integer taskId, TaskCallable taskCallable) {
        this.paramType = paramType;
        this.taskId = taskId;
        this.taskCallable = taskCallable;
    }
}
