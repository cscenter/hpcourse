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

    /**
     * Blocking value get operation
     * @return associated value
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
