import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * @author Ruslan Akhundov
 */
public class FutureImplTest {
    private final Object monitor = new Object();
    private WaitingTask task;
    private ThreadPoolImpl.FutureImpl future;
    private Thread workerThread;
    private static final String result = "result";

    @Before
    public void setUp() {
        task = new WaitingTask();
        ThreadPoolImpl threadPool = new ThreadPoolImpl(10);
        future = threadPool.new FutureImpl<>(task, result, 0);
        workerThread = new Thread(future);
    }


    @Test
    public void testCancel_1() throws Exception {
        future.cancel(false);
        workerThread.start();
        workerThread.join();
        assertEquals(0, task.state.get());
        assertTrue(future.isCancelled());
    }

    @Test
    public void testCancel_2() throws Exception {
        future.cancel(true);
        workerThread.start();
        workerThread.join();
        assertEquals(0, task.state.get());
        assertTrue(future.isCancelled());
    }

    @Test
    public void testCancel_3() throws Exception {
        workerThread.start();
        while (task.state.get() != 1) Thread.yield();
        future.cancel(true);
        workerThread.join();
        assertEquals(4, task.state.get());
        assertTrue(future.isCancelled());
    }

    @Test
    public void testCancel_4() throws Exception {
        workerThread.start();
        while (task.state.get() != 1) Thread.yield();
        future.cancel(false);
        synchronized (monitor) {
            monitor.notifyAll();
        }
        workerThread.join();
        assertEquals(2, task.state.get());
        assertFalse(future.isCancelled());
    }

    @Test
    public void testIsDone() throws Exception {
        workerThread.start();
        while (task.state.get() != 1) Thread.yield();
        synchronized (monitor) {
            monitor.notifyAll();
        }
        workerThread.join();
        assertEquals(2, task.state.get());
        assertTrue(future.isDone());
    }

    @Test
    public void testGet_1() throws Exception {
        workerThread.start();
        while (task.state.get() != 1) Thread.yield();
        synchronized (monitor) {
            monitor.notifyAll();
        }
        workerThread.join();
        assertEquals(2, task.state.get());
        assertTrue(future.isDone());
        assertEquals(result, future.get());
    }

    @Test
    public void testGet_2() throws Exception {
        workerThread.start();
        try {
            future.get(100, TimeUnit.MILLISECONDS);
            assertEquals(1, 2);
        } catch (TimeoutException e) {
            //expected
        }
        while (task.state.get() != 1) Thread.yield();
        synchronized (monitor) {
            monitor.notifyAll();
        }
        workerThread.join();
        assertEquals(result, future.get(100, TimeUnit.MILLISECONDS));
        assertEquals(2, task.state.get());
        assertTrue(future.isDone());
    }

    @Test
    public void testGetAfterCancellation() throws Exception {
        workerThread.start();
        future.cancel(true);
        workerThread.join();
        assertTrue(future.isCancelled());
        try {
            future.get();
            assertEquals(2, 1);
        } catch (CancellationException e) {
            //expected
        }
    }

    public class WaitingTask implements Runnable {
        public final AtomicInteger state = new AtomicInteger(0);

        @Override
        public void run() {
            try {
                synchronized (monitor) {
                    state.incrementAndGet();
                    monitor.wait();
                }
            } catch (InterruptedException e) {
                state.set(4);
                return;
            }
            state.incrementAndGet();
        }
    }
}