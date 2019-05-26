package ru.compscicenter.mlogachev;

import org.junit.Test;
import static org.junit.Assert.*;


public class LockFreeSetImplTest {
    @Test
    public void AddingSomeWorks() {
        LockFreeSet<Integer> lockFreeSet = new LockFreeSetImpl<>();

        lockFreeSet.add(2);
        lockFreeSet.add(3);

        assertTrue(lockFreeSet.contains(2));
        assertTrue(lockFreeSet.contains(3));
        assertFalse(lockFreeSet.contains(1));
    }

    @Test
    public void RemovingWorksAsWell() {
        LockFreeSet<Integer> lockFreeSet = new LockFreeSetImpl<>();

        lockFreeSet.add(2);
        lockFreeSet.add(3);

        assertTrue(lockFreeSet.contains(2));
        assertTrue(lockFreeSet.contains(3));

        lockFreeSet.remove(2);
        assertFalse(lockFreeSet.contains(2));
        assertTrue(lockFreeSet.contains(3));

        lockFreeSet.remove(3);
        assertFalse(lockFreeSet.contains(3));

        assertTrue(lockFreeSet.isEmpty());
    }

    @Test
    public void IsReallyASet() {
        LockFreeSet<Integer> lockFreeSet = new LockFreeSetImpl<>();

        lockFreeSet.add(2);
        lockFreeSet.add(3);
        lockFreeSet.add(4);

        assertTrue(lockFreeSet.contains(3));

        assertFalse(lockFreeSet.add(3));
        assertFalse(lockFreeSet.add(4));

        lockFreeSet.remove(3);
        assertFalse(lockFreeSet.contains(3));
        lockFreeSet.remove(4);
        assertFalse(lockFreeSet.contains(4));

        assertFalse(lockFreeSet.add(2));
        assertTrue(lockFreeSet.contains(2));
    }

    @Test
    public void IsEmptyKindaWorks() {
        LockFreeSet<Integer> lockFreeSet = new LockFreeSetImpl<>();

        lockFreeSet.add(3);
        assertFalse(lockFreeSet.isEmpty());
    }
}
