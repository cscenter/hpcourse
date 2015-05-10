package hw;

import org.junit.Test;

import java.util.Optional;
import java.util.concurrent.Callable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FixedThreadPoolTest {
    private final int TEST_TIMEOUT_MS = 5 * 1000;

    @Test(expected = UserException.class)
    public void testUserException() throws Exception {
        Future exceptionTask = new Future(() -> {
            throw new UserException();
        });
        submit(exceptionTask);
        throw exceptionTask.getException();
    }

    @Test
    public void testResult() throws InterruptedException {
        Future exceptionTask = new Future(() -> {
            return 4;
        });
        submit(exceptionTask);
        assertEquals(4, exceptionTask.getResult());
    }

    @Test(timeout = TEST_TIMEOUT_MS)
    public void ableToCancel() throws InterruptedException {
        int INF = 100500 * 1000;
        Future task = sleepTask(INF);
        FixedThreadPool pool = new FixedThreadPool(1);
        pool.submit(task);
        submit(task);
        task.cancel();
        pool.join();
    }

    @Test(timeout = TEST_TIMEOUT_MS)
    public void interruptedExceptionWasCalledOnCancel() throws InterruptedException {
        InterruptedExceptionSpy spy = new InterruptedExceptionSpy();
        Future task = new Future(spy);
        FixedThreadPool pool = new FixedThreadPool(1);
        pool.submit(task);
        submit(task);
        task.cancel();
        pool.join();
        assertTrue(spy.interruptionExceptionWasThrown);
    }

    // doubles
    private Future sleepTask(int durationMs) {
        return new Future(() -> {
            Thread.sleep(durationMs);
            return Optional.empty();
        });
    }

    // utils
    private void submit(Future f) throws InterruptedException {
        FixedThreadPool pool = new FixedThreadPool(1);
        pool.submit(f);
        pool.join();
    }

    private class InterruptedExceptionSpy implements Callable {
        public boolean interruptionExceptionWasThrown;

        public Object call() {
            int INF = 100500 * 1000;
            try {
                Thread.sleep(INF);
            } catch (InterruptedException e) {
                interruptionExceptionWasThrown = true;
            }
            return null;
        }
    }
}