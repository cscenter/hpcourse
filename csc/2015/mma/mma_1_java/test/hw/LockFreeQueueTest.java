package hw;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LockFreeQueueTest {

    private IQueue<String> q = new LockFreeQueue<>();

    @Test
    public void testIsEmpty() throws Exception {
        q.add("1");
        q.add("2");
        q.poll();
        q.poll();
        assertTrue(q.isEmpty());
    }

    @Test
    public void testAdd() throws Exception {
        q.add("1");
        q.add("2");
        assertEquals("1", q.poll());
    }
}