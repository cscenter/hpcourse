package hw;

import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertEquals;

public class FixedThreadPoolTest {
    @Test(expected = UserException.class)
    public void testUserException() throws Exception {
        Future exceptionTask = new Future(() -> {
            throw new UserException();
        });
        submit(exceptionTask);
        throw exceptionTask.getException();
    }

    @Test
    public void testResult() {
        Future exceptionTask = new Future(() -> {
            return 4;
        });
        submit(exceptionTask);
        assertEquals(4, exceptionTask.getResult());
    }

    @Test
    public void fooling() throws Exception {
        FixedThreadPool man = new FixedThreadPool(1);
        long duration_secs = 10;
        man.submit(new Future(() -> {
            Thread.sleep(duration_secs * 1000);
            return Optional.empty();
        }));
        man.submit(new Future(() -> {
            Thread.sleep(duration_secs * 1000);
            return Optional.empty();
        }));
    }


    private void submit(Future f) {
        FixedThreadPool pool = new FixedThreadPool(1);
        pool.submit(f);
        pool.join();
    }
}