package util;

/**
 * Created by nikita.sokeran@gmail.com
 */
public class ValueWrapper<T> {

    private final Class<? extends T> clazz;
    private T value;

    public ValueWrapper(final Class<? extends T> clazz) {
        this.clazz = clazz;
    }

    public T getValue() {
        return value;
    }

    /**
     * @param value
     * @return {@code true} if value can be casted to {@code clazz} value passed to constructor, or {@code false} otherwise
     */
    public boolean setValue(final T value) {
        try {
            clazz.cast(value);
        } catch (ClassCastException ex) {
            return false;
        }

        this.value = value;
        return true;
    }
}
