package hw;

public enum TaskStatus {
    NOTSTARTED(1), RUNNING(2), DONE(3), CANCELLED(4);

    private final int value;

    private TaskStatus(int value) {
        this.value = value;
    }

    public static TaskStatus get(int value) {
        for (TaskStatus values : TaskStatus.values()) {
            if (values.value == value) {
                return values;
            }
        }
        return null;
    }

    public int getValue() {
        return value;
    }
}


