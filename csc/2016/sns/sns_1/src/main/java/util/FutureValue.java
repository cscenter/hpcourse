package util;

/**
 * Created by nikita.sokeran@gmail.com
 */
public class FutureValue<T> {

    private final ValueWrapper<? super T> valueWrapper;
    private final Class<T> clazz;

    public FutureValue(final ValueWrapper<? super T> valueWrapper, final Class<T> clazz) {
        this.valueWrapper = valueWrapper;
        this.clazz = clazz;
    }

    public boolean isEmpty() {
        return valueWrapper.getValue() == null;
    }

    /**
     * Block current thread until value coming
     *
     * @return
     * @throws InterruptedException
     */
    public T get() throws InterruptedException {
        synchronized (valueWrapper) {
            while (valueWrapper.getValue() == null) {
                valueWrapper.wait();
            }

            try {
                return clazz.cast(valueWrapper.getValue());
            } catch (ClassCastException e) {
                return null;
            }
        }
    }

    public T tryGet() {
        try {
            return clazz.cast(valueWrapper.getValue());
        } catch (ClassCastException e) {
            return null;
        }
    }
}
