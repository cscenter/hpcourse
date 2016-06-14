package client.params;

/**
 * @author Dmitriy Tseyler
 */
public class Parameter {
    private final boolean depends;
    private final long value;

    public Parameter(long value, boolean depends) {
        this.value = value;
        this.depends = depends;
    }

    public boolean isDepends() {
        return depends;
    }

    public long getValue() {
        return value;
    }
}
