package hw;

import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertEquals;

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

    @Test
    public void fooling() throws Exception {
        FixedThreadPool man = new FixedThreadPool(1);
        man.submit(sleepTask(10 * 1000));
        man.submit(sleepTask(10 * 1000));
    }

    private Future sleepTask(int durationMs){
        return new Future(() -> {
            Thread.sleep(durationMs);
            return Optional.empty();
        });
    }

    private void submit(Future f) throws InterruptedException {
        FixedThreadPool pool = new FixedThreadPool(1);
        pool.submit(f);
        pool.join();
    }
}