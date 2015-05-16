import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.Queue;

public class BlockingQueueImpl<T> {

    private final Queue<T> queue;
    private final int CAPACITY;

    public BlockingQueueImpl(int capacity) {
        CAPACITY = capacity;
        queue = new ArrayDeque<>(CAPACITY);
    }

    public void push(@NotNull T value) throws InterruptedException {
        synchronized (queue) {
            while (queue.size() == CAPACITY)
                queue.wait();
            queue.add(value);
            queue.notify();
        }
    }

    public @NotNull T pop() throws InterruptedException {
        synchronized (queue) {
            while (queue.size() == 0)
                queue.wait();
            final T value = queue.poll();
            queue.notify();
            return value;
        }
    }

    public int size() {
        return queue.size();
    }
}
