package hw;

/**
 * Created by maxim on 5/11/15.
 */
public interface IQueue <T> {
    boolean isEmpty();
    void add(T item);
    T poll();
}
