package hw;

import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.*;

public class FutureTest {

    private final int TEST_TIMEOUT_MS = 5 * 1000;

    @Test
    public void cancelNotStarted() throws Exception {
        Future task = dummyTask();
        assertFalse(task.cancel());
        assertEquals(TaskStatus.CANCELLED, task.getStatus());
    }

    @Test
    public void cancelDone() throws Exception {
        Future task = dummyTask();
        submit(task);
        assertTrue(task.cancel());
        assertEquals(TaskStatus.DONE, task.getStatus());
    }

    @Test
    public void cancelDoneSubsequent() throws Exception {
        Future task = dummyTask();
        submit(task);
        task.cancel();
        task.cancel();
        assertTrue("subsequent calls to isDone() will always return true.", task.cancel());
        assertEquals(TaskStatus.DONE, task.getStatus());
    }

    @Test(timeout = TEST_TIMEOUT_MS)
    public void cancelRunning() throws Exception {
        Future task = longRunningTask();
        FixedThreadPool pool = submit(task, false);
        assertFalse(task.cancel());
        pool.join();
        assertEquals(TaskStatus.CANCELLED, task.getStatus());
    }

    @Test
    public void cancelCancelledTrue() throws Exception {
        Future done = dummyTask();
        submit(done);
        done.cancel();
        assertTrue("Subsequent calls to isCancelled() will always return true if this method returned true.",
                done.cancel());
        assertEquals(TaskStatus.DONE, done.getStatus());
    }

    // doubles
    private Future longRunningTask() {
        int INF = 100500 * 1000;
        return new Future(() -> {
            Thread.sleep(INF);
            return Optional.empty();
        });
    }

    private Future dummyTask() {
        return new Future(() -> {
            return 42;
        });
    }

    // utils
    private void submit(Future f) throws InterruptedException {
        submit(f, true);
    }

    private FixedThreadPool submit(Future f, boolean join) throws InterruptedException {
        FixedThreadPool pool = new FixedThreadPool(1);
        pool.submit(f);
        if (join) {
            pool.join();
        }
        return pool;
    }
}