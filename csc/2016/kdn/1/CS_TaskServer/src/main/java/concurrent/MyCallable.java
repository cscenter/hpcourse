package concurrent;

/**
 * Created by dkorolev on 4/9/2016.
 */
public interface MyCallable<T> {
    T call() throws Exception;
}
