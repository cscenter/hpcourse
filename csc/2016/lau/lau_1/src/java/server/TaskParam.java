package server;

public class TaskParam {

    public enum Type {
        VALUE,
        TASK_ID
    }

    long value;
    int dependentTaskId;
    Type type;

    public TaskParam(Type type, long value) {
        this.value = value;
        this.dependentTaskId = (int)value;
        this.type = type;
    }

    @Override
    public String toString() {
        return type.toString() + " : " + (type == Type.VALUE ? value : dependentTaskId);
    }

    public Type getType() {
        return type;
    }

    public long getValue() {
        return value;
    }

    public int getDependentTaskId() {
        return dependentTaskId;
    }
}
