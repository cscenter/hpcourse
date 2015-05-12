package hw;

import hw.threadpool.ThreadPoolFactory;
import org.junit.Test;

import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.*;

public class FutureTest {

    private final int TEST_TIMEOUT_MS = 5 * 1000;

    private ThreadPool pool = ThreadPoolFactory.create(1);

    @Test
    public void cancelNotStarted() throws Exception {
        Future task = dummyTask();
        assertFalse(task.cancel());
        assertEquals(TaskStatus.CANCELLED, task.getStatus());
    }

    @Test
    public void cancelDone() throws Exception {
        Future task = dummyTask();
        pool.submit(task);
        task.get();
        assertTrue(task.cancel());
        assertEquals(TaskStatus.DONE, task.getStatus());
    }

    @Test
    public void cancelDoneSubsequent() throws Exception {
        Future done = dummyTask();
        pool.submit(done);
        done.get();
        done.cancel();
        assertTrue("subsequent calls to isDone() will always return true.", done.cancel());
        assertEquals(TaskStatus.DONE, done.getStatus());
    }

    @Test(timeout = TEST_TIMEOUT_MS, expected = CancellationException.class)
    public void cancelRunning() throws Exception {
        Future task = longRunningTask();
        pool.submit(task);
        assertFalse(task.cancel());
        try {
            task.get();
        }catch (CancellationException ce) {
            assertEquals(TaskStatus.CANCELLED, task.getStatus());
            throw ce;
        }
    }


    @Test(expected = ExecutionException.class)
    public void testException() throws Exception {
        Future exceptionTask = new Future(() -> {
            throw new Exception();
        });
        pool.submit(exceptionTask);
        exceptionTask.get();
    }

    @Test
    public void testResult() throws Exception {
        Future resultTask = new Future(() -> {
            return 4;
        });
        pool.submit(resultTask);
        assertEquals(4, resultTask.get());
    }

    @Test(timeout = TEST_TIMEOUT_MS, expected = CancellationException.class)
    public void ableToCancel() throws Exception {
        Future task = longRunningTask();
        pool.submit(task);
        task.cancel();
        task.get();
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
}