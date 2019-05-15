package kornilova.set;

import org.junit.Test;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Random;
import java.util.Set;

import static junit.framework.TestCase.*;

public class LockFreeSetTest {
    private static final long SEED = 10;
    private static final int MAX_ITEM = 10;
    private static final float PERCENT_OF_ADDITIONS = 0.7f;

    @Test
    public void addRemoveAdd() {
        int n = 1;
        LockFreeSet<Integer> s = empty();
        assertTrue(s.add(n));
        assertFalse(s.add(n));
        assertFalse(s.isEmpty());

        assertTrue(s.remove(n));
        assertFalse(s.remove(n));
        assertTrue(s.isEmpty());

        assertTrue(s.add(n));
        assertFalse(s.add(n));
        assertFalse(s.isEmpty());
    }

    @Test
    public void simple() {
        LockFreeSet<Integer> s = empty();
        assertFalse(s.remove(-3));
        assertTrue(s.add(2));
        assertFalse(s.remove(-6));
        assertTrue(s.add(-7));
        assertFalse(s.isEmpty());
        assertTrue(s.add(9));
        assertFalse(s.add(9));
    }

    @Test
    public void testIterator() {
        LockFreeSet<Integer> s = empty();
        int n = 100;
        int n2 = 10;
        for (int i = 0; i < n; i++) {
            assertTrue(s.add(i));
        }
        Set<Integer> items = new HashSet<>();
        Iterator<Integer> it = s.iterator();

        for (int i = n; i < n + n2; i++) {
            assertTrue(s.add(i));
        }

        while (it.hasNext()) {
            items.add(it.next());
        }

        assertEquals(n, items.size());
        for (int i = 0; i < n; i++) {
            assertTrue(items.contains(i));
        }
    }

    @Test
    public void addRemoveAdd2() {
        LockFreeSet<Integer> s = empty();
        int n = 100;
        for (int i = 0; i < n; i++) {
            assertTrue(s.add(i));
        }
        assertFalse(s.isEmpty());
        for (int i = 0; i < n; i++) {
            assertTrue(s.contains(i));
        }
        for (int i = 0; i < n; i++) {
            assertTrue(s.remove(i));
        }
        for (int i = 0; i < n; i++) {
            assertFalse(s.contains(i));
        }
        assertTrue(s.isEmpty());
    }

    @Test
    public void addRandoms() {
        LockFreeSet<Integer> s = empty();
        Random rnd = rnd();
        for (int i = 0; i < 100; i++) {
            int r = rnd.nextInt() % MAX_ITEM;

            boolean prePresent = s.contains(r);
            boolean added = s.add(r);
            assertTrue(s.contains(r));
            assertFalse(added == prePresent);
        }
    }

    @Test
    public void addAndRemoveRandoms() {
        LockFreeSet<Integer> s = empty();
        Random rnd = rnd();
        for (int i = 0; i < 100; i++) {
            int v = rnd.nextInt(MAX_ITEM);

            if (rnd.nextFloat() < PERCENT_OF_ADDITIONS) {
                boolean prePresent = s.contains(v);
                boolean added = s.add(v);
                assertTrue(s.contains(v));
                assertFalse(added == prePresent);
            } else {
                boolean prePresent = s.contains(v);
                boolean removed = s.remove(v);
                assertFalse(s.contains(v));
                assertEquals(prePresent, removed);
            }
        }
    }

    private <T extends Comparable<T>> LockFreeSet<T> empty() {
        return new LockFreeSetImpl<>();
    }

    private Random rnd() {
        return new Random(SEED);
    }
}
