package hw;


public interface ThreadPool {
    void submit(Runnable task);

    void awaitAll() throws InterruptedException;
}
