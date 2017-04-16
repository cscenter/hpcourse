package client.params;

/**
 * @author Dmitriy Tseyler
 */
public interface MessageParams<T> {
    void configure(T message);
}
