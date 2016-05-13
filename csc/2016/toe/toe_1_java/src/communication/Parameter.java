package communication;


public class Parameter {
    private volatile Long value;
    private Task owner;

    public Parameter(long value) {
        this.value = value;
    }

    public Parameter(Task owner) throws IllegalArgumentException {
        if (owner == null) {
            throw new IllegalArgumentException("Owner task must not be null.");
        }
        this.owner = owner;
    }

    public final long getValue() {
        if (value == null) {
            value = owner.getResult();
        }
        return value;
    }
}
