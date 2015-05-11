package hw;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class LockFreeQueueConcurrentTest {

    // should be divisible to N_WRITERS and N_READERS
    private final int NVALUES = 10;
    private final int N_WRITERS = 2;
    private final int N_READERS = 2;
    private IQueue<Integer> q;

    @Before
    public void setUp() throws Exception {
        if (NVALUES % N_WRITERS != 0 || NVALUES % N_READERS != 0) {
            throw new Exception("NVALUES should be divisible to N_WRITERS and N_READERS");
        }
        q = new LockFreeQueue<>();
    }

    @Test
    public void testWrite() throws Exception {

        int n_per_writer = NVALUES / N_WRITERS;
        Thread[] wThreads = new Thread[N_WRITERS];
        for (int i = 0; i < N_WRITERS; i++) {
            Writer w = new Writer(q, i * n_per_writer, (i + 1) * n_per_writer);
            wThreads[i] = new Thread(w);
        }
        for (int i = 0; i < N_WRITERS; i++) {
            wThreads[i].start();
        }

        for (int i = 0; i < N_WRITERS; i++) {
            wThreads[i].join();
        }

        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < NVALUES; i++) {
            assertNotNull(q);
            list.add(q.poll());
        }
        assertTrue(q.isEmpty());
    }

    @Test
    public void testReadWrite() throws Exception {
        int n_per_writer = NVALUES / N_WRITERS;
        int n_per_reader = NVALUES / N_WRITERS;
        Thread[] wThreads = new Thread[N_WRITERS];
        for (int i = 0; i < N_WRITERS; i++) {
            Writer w = new Writer(q, i * n_per_writer, (i + 1) * n_per_writer);
            wThreads[i] = new Thread(w);
        }

        Thread[] rThreads = new Thread[N_WRITERS];
        for (int i = 0; i < N_WRITERS; i++) {
            Reader w = new Reader(q, n_per_reader);
            rThreads[i] = new Thread(w);
        }

        for (int i = 0; i < N_WRITERS; i++) {
            wThreads[i].start();
        }

        for (int i = 0; i < N_WRITERS; i++) {
            rThreads[i].join();
        }

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
            for (int i = 0; i < elapsed; i++) {
                Integer item = q.poll();
                if (item != null) {
                    elapsed--;
                }
            }
        }
    }
}