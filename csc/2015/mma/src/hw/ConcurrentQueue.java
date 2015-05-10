package hw;


import java.util.concurrent.ConcurrentLinkedQueue;

public class ConcurrentQueue {
    //todo:implement one
    private final ConcurrentLinkedQueue<Future> impl = new ConcurrentLinkedQueue<Future>();

    public void add(Future item) {
        impl.add(item);
    }

    public Future poll() {
        return impl.poll();
    }

    public boolean isEmpty() {
        return impl.isEmpty();
    }
}
