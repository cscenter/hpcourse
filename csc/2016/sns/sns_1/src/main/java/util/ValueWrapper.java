package util;

/**
 * Simple value wrapper with blocking get operation
 *
 * @param <T> storing value type
 */
public class ValueWrapper<T> {
    private final Class<? extends T> clazz;
    private final Object lock = new Object();
    private volatile T value;

    /**
     * @param clazz before returning value will be casted to this class
     */
    public ValueWrapper(final Class<? extends T> clazz) {
        this.clazz = clazz;
    }

    /**
     * Blocking operation
     *
     * @return storing value, can return {@code null} if method interrupted, but can return actual value if lucky enough
     */
    public T getValue() {
        //Double-checking for less synchronization during work with this method
        if (value == null) {
            synchronized (lock) {
                while (value == null) {
                    try {
                        lock.wait();
                    } catch (InterruptedException e) {
                        return value;
                    }
                }
            }
        }
        return value;
    }

    /**
     * @param value to write to wrapper
     * @throws CheckedClassCastException when value can't be casted to class {@code clazz} which was passed to constructor
     */
    public void setValue(final T value) throws CheckedClassCastException {
        synchronized (lock) {
            try {
                clazz.cast(value);
            } catch (ClassCastException ex) {
                throw new CheckedClassCastException("ValueWrapper class can't cast value with type T to type " + clazz.getName(), ex);
            }
            this.value = value;
            lock.notifyAll();
        }
    }

    public boolean hasValue() {
        return value != null;
    }

    public Class<? extends T> getClazz() {
        return clazz;
    }
}
