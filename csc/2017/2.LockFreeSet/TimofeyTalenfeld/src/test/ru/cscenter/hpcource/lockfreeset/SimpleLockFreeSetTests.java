package ru.cscenter.hpcource.lockfreeset;

import org.junit.Test;

import static org.junit.Assert.*;

public class SimpleLockFreeSetTests {

    @Test
    public void isEmptyTest() {
        LockFreeSet<String> set = new LockFreeSetImpl<>();
        assertEquals(true, set.isEmpty());
    }

    @Test
    public void isNotEmptyTest() {
        LockFreeSet<String> set = new LockFreeSetImpl<>();
        set.add("test");
        assertEquals(false, set.isEmpty());
    }

    @Test
    public void containsTest() {
        String value = "test";
        LockFreeSet<String> set = new LockFreeSetImpl<>();
        set.add(value);
        assertEquals(true, set.contains(value));
    }

    @Test
    public void notContainsTest() {
        String value = "test";
        String mockValue = "";
        LockFreeSet<String> set = new LockFreeSetImpl<>();
        set.add(mockValue);
        assertEquals(false, set.contains(value));
    }

    @Test
    public void addTest() {
        String value = "test";
        LockFreeSet<String> set = new LockFreeSetImpl<>();
        assertEquals(true, set.add(value));
        assertEquals(true, set.contains(value));
    }

    @Test
    public void rejectTest() {
        String value = "test";
        LockFreeSet<String> set = new LockFreeSetImpl<>();
        assertEquals(true, set.add(value));
        assertEquals(true, set.contains(value));
        assertEquals(false, set.add(value));
        assertEquals(true, set.add(value));
    }

    @Test
    public void removeTest() {
        String value = "test";
        LockFreeSet<String> set = new LockFreeSetImpl<>();
        set.add(value);
        assertEquals(true, set.remove(value));
        assertEquals(false, set.contains(value));
    }

    @Test
    public void doNotRemoveTest() {
        String value = "test";
        LockFreeSet<String> set = new LockFreeSetImpl<>();
        assertEquals(false, set.remove(value));
        assertEquals(false, set.contains(value));
    }
}
