package ru.cscenter;

import static org.junit.Assert.*;

/**
 * Created by me on 19.04.17.
 */
public class LockFreeSetImplTest {

    LockFreeSetImpl<Integer> set = null;

    @org.junit.Before
    public void setUp() throws Exception {
        set = new LockFreeSetImpl<>();
    }

    @org.junit.After
    public void tearDown() throws Exception {
        set = null;
    }

    @org.junit.Test
    public void add() throws Exception {
        assertTrue(set.add(10));
        assertTrue(set.add(15));
        assertFalse(set.add(10));
        assertFalse(set.add(15));
        assertTrue(set.remove(10));
        assertTrue(set.remove(15));
        assertTrue(set.add(10));
        assertTrue(set.add(15));
        assertTrue(set.remove(10));
        assertTrue(set.remove(15));
        assertTrue(set.isEmpty());
    }

    @org.junit.Test
    public void remove() throws Exception {
        assertFalse(set.remove(10));
        assertFalse(set.remove(10));
        assertTrue(set.add(10));
        assertFalse(set.add(10));
        assertTrue(set.remove(10));
        assertFalse(set.remove(10));
        assertTrue(set.isEmpty());
    }

    @org.junit.Test
    public void contains() throws Exception {
        assertTrue(set.add(10));
        assertTrue(set.contains(10));
        assertFalse(set.add(10));
        assertTrue(set.contains(10));
        assertTrue(set.remove(10));
        assertFalse(set.contains(10));
        assertFalse(set.remove(10));
        assertTrue(set.isEmpty());
    }

    @org.junit.Test
    public void isEmpty() throws Exception {
        assertTrue(set.isEmpty());
        set.add(10);
        assertFalse(set.isEmpty());
        set.remove(10);
        assertTrue(set.isEmpty());
    }

}