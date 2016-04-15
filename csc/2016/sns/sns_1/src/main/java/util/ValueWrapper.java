package util;

/**
 * Created by nikita.sokeran@gmail.com
 */
public class ValueWrapper<T> {
    private final Class<? extends T> clazz;
    private final Object lock = new Object();
    private volatile T value;

    public ValueWrapper(final Class<? extends T> clazz) {
        this.clazz = clazz;
    }

    /**
     * Block thread and wait
     *
     * @return
     */
    public T getValue() {
        synchronized (lock) {
            while (value == null) {
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    return null;
                }
            }

            return value;
        }
    }

    /**
     * @param value
     * @throws CheckedClassCastException when value can't be casted to class which was passed to constructor
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

    public Class<? extends T> getClazz() {
        return clazz;
    }
}
