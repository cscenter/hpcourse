package hw;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class LockFreeQueueConcurrentTest {

    // should be divisible to N_WRITERS and N_READERS
    private final int NVALUES = 10;
    private final int N_WRITERS = 2;
    private final int N_READERS = 2;
    int N_PER_WRITER = NVALUES / N_WRITERS;
    int N_PER_READER = NVALUES / N_WRITERS;
    private IQueue<Integer> q;

    private static void runAndJoin(Iterable<Thread> threads) throws Exception {
        for (Thread t : threads) {
            t.start();
        }

        for (Thread t : threads) {
            t.join();
        }
    }

    @Before
    public void setUp() throws Exception {
        if (NVALUES % N_WRITERS != 0 || NVALUES % N_READERS != 0) {
            throw new Exception("NVALUES should be divisible to N_WRITERS and N_READERS");
        }
        q = new LockFreeQueue<>();
    }

    @Test
    public void testAsyncWrite() throws Exception {
        List<Thread> threads = new LinkedList<>();
        for (int i = 0; i < N_WRITERS; i++) {
            Writer w = new Writer(q, i * N_PER_WRITER, (i + 1) * N_PER_WRITER);
            threads.add(new Thread(w));
        }
        runAndJoin(threads);
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < NVALUES; i++) {
            assertNotNull(q);
            list.add(q.poll());
        }
        assertTrue(q.isEmpty());
    }

    @Test
    public void testAsyncRead() throws Exception {
        for (int i = 0; i < NVALUES; i++) {
            q.add(i);
        }
        List<Thread> threads = new LinkedList<>();
        for (int i = 0; i < N_READERS; i++) {
            Reader r = new Reader(q, N_PER_READER);
            threads.add(new Thread(r));
        }
        runAndJoin(threads);
        assertTrue(q.isEmpty());
    }

    @Test
    public void testAsyncReadWrite() throws Exception {
        List<Thread> threads = new LinkedList<>();
        for (int i = 0; i < N_WRITERS; i++) {
            Writer w = new Writer(q, i * N_PER_WRITER, (i + 1) * N_PER_WRITER);
            threads.add(new Thread(w));
        }
        for (int i = 0; i < N_READERS; i++) {
            Reader r = new Reader(q, N_PER_READER);
            threads.add(new Thread(r));
        }
        runAndJoin(threads);
        assertTrue(q.isEmpty());
    }

    private class Writer implements Runnable {
        private final int from;
        private final int to;
        private final IQueue<Integer> q;

        public Writer(IQueue<Integer> q, int from, int to) {
            this.from = from;
            this.to = to;
            this.q = q;
        }

        @Override
        public void run() {
            for (int i = from; i < to; i++) {
                q.add(i);
            }
        }
    }

    private class Reader implements Runnable {
        private final IQueue<Integer> q;
        private int elapsed;

        public Reader(IQueue<Integer> q, int n) {
            elapsed = n;
            this.q = q;
        }

        @Override
        public void run() {
            while (elapsed > 0) {
                Integer item = q.poll();
                if (item != null) {
                    elapsed--;
                }
            }
        }
    }
}