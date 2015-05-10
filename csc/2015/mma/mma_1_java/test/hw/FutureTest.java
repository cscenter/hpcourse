package hw;

import org.junit.Test;

import static org.junit.Assert.*;

public class FutureTest {

    @Test
    public void notStartedCancelled() throws Exception {
        Future notStarted = new Future(() -> {
            return 42;
        });
        assertTrue(notStarted.cancel());
        assertEquals(TaskStatus.CANCELLED, notStarted.getStatus());
    }

    @Test
    public void doneCancelled() throws Exception {
        Future done = new Future(() -> {
            return 42;
        });
        submit(done);
        assertFalse(done.cancel());
        assertEquals(TaskStatus.DONE, done.getStatus());
    }

    private void submit(Future f) {
        FixedThreadPool pool = new FixedThreadPool(1);
        pool.submit(f);
        pool.join();
    }
}