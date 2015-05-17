package hw;


public interface ThreadPool {
    void submit(Future task);

    void awaitCompletion() throws InterruptedException;
}
