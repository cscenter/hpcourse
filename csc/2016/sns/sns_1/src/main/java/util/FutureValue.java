package util;

/**
 * Basic implementation of java.util.concurrent.Future
 * @see java.util.concurrent.Future
 * @param <T> type of storing value
 */
public class FutureValue<T> {

    private final ValueWrapper<? super T> valueWrapper;
    private final Class<T> clazz;

    /**
     * @param valueWrapper where value is storing
     * @param clazz value will be casted to this class type before returning
     */
    public FutureValue(final ValueWrapper<? super T> valueWrapper, final Class<T> clazz) {
        this.valueWrapper = valueWrapper;
        this.clazz = clazz;
    }

    /**
     * Blocking operation
     * @return value casted to {@code clazz} type or {@code null} if this cast is prohibited
     */
    public T get() {
        try {
            valueWrapper.getClazz().cast(valueWrapper.getValue());
            return clazz.cast(valueWrapper.getValue());
        } catch (ClassCastException e) {
            return null;
        }
    }
}
