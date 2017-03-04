package com.ashatta.hps.server.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class LockTests {
    private class TryToLockAndAddToList implements Runnable {
        private final Lock lock;
        private final List<String> list;
        private final String stringToAdd;

        public TryToLockAndAddToList(Lock lock, List<String> list, String stringToAdd) {
            this.lock = lock;
            this.list = list;
            this.stringToAdd = stringToAdd;
        }

        public void run() {
            lock.lock();
            list.add(stringToAdd);
            lock.unlock();
        }
    }

    @Test
    public void testLockNoUnlock() throws InterruptedException {
        Lock lock = new Lock();
        List<String> list = new ArrayList<>();

        lock.lock();
        new Thread(new TryToLockAndAddToList(lock, list, "A")).start();
        Thread.sleep(200);

        assertTrue(list.isEmpty());
    }

    @Test
    public void testLockOrder() throws InterruptedException {
        Lock lock = new Lock();
        List<String> list = new ArrayList<>();

        lock.lock();
        new Thread(new TryToLockAndAddToList(lock, list, "A")).start();
        Thread.sleep(200);
        list.add("B");
        lock.unlock();
        Thread.sleep(200);

        assertEquals(2, list.size());
        assertEquals("B", list.get(0));
        assertEquals("A", list.get(1));
    }
}
