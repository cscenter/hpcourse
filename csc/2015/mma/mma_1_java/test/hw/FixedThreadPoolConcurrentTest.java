package hw;

import hw.threadpool.ThreadPoolFactory;
import org.junit.Test;

import java.util.Optional;
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

        pool.awaitAll();

        assertEquals(N_TASKS, inc.get());
    }
}