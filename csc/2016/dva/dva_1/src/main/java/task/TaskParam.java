package task;

public class TaskParam {
    private volatile Long value;
    private final TaskThread sourceTask;

    public TaskParam(long value) {
        this.value = value;
        sourceTask = null;
    }

    public TaskParam(TaskThread paramSource) throws IllegalArgumentException {
        if (paramSource == null) {
            throw new IllegalArgumentException("TaskThread must be not null");
        }
        this.value = null;
        this.sourceTask = paramSource;
    }

    public final Integer getTaskId() {
        if (sourceTask == null)
            return null;
        else
            return sourceTask.id;
    }

    public final long getValue() {
        if (value != null) {
            return value;
        } else {
            value = sourceTask.getResult();
            return value;
        }
    }
}
