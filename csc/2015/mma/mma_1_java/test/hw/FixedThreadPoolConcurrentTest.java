package hw;

import hw.threadpool.ThreadPoolFactory;
import org.junit.Test;

import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

public class FixedThreadPoolConcurrentTest {
    private final int TEST_TIMEOUT_MS = 500 * 1000;

    private final int N_TASKS = 100500;
    private final int N_THREADS = 30;

    @Test(timeout = TEST_TIMEOUT_MS)
    public void testCalculation() throws Exception {
        ThreadPool pool = ThreadPoolFactory.create(N_THREADS);

        AtomicInteger inc = new AtomicInteger(0);

        for (int i = 0; i < N_TASKS; i++) {
            Future incTask = new Future(() -> {
                inc.incrementAndGet();
                return Optional.empty();
            });
            pool.submit(incTask);
        }

        pool.awaitCompletion();

        assertEquals(N_TASKS, inc.get());
    }

    @Test(timeout = TEST_TIMEOUT_MS, expected = IllegalStateException.class)
    public void unableToSubmitTaskAfterCompletionRequest() throws Throwable {
        ThreadPool pool = ThreadPoolFactory.create(1);

        Future shutdown = new Future(() -> {
            pool.submit(dummyTask());
            return Optional.empty();
        });

        pool.submit(shutdown);
        pool.awaitCompletion();

        try {
            shutdown.get();
        } catch (ExecutionException e) {
            throw e.getCause();
        }
    }

    @Test(timeout = TEST_TIMEOUT_MS)
    public void emptyThreadPoolCompletion() throws Exception {
        ThreadPool pool = ThreadPoolFactory.create(1);
        pool.awaitCompletion();
    }

    private Future dummyTask() {
        return new Future(() -> {
            return 42;
        });
    }
}