package com.ashatta.hps.server.internal;

import java.util.concurrent.atomic.AtomicBoolean;

public class Lock {

    private final AtomicBoolean lock = new AtomicBoolean(false);

    public void lock() {
        int sleeptime = 1;
        while (lock.getAndSet(true)) {
            try {
                Thread.sleep(sleeptime);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (sleeptime <= 64) {
                sleeptime = 2 * sleeptime;
            }
        }
    }

    public void unlock() {
        if (!lock.get()) {
            throw new IllegalStateException();
        }
        lock.set(false);
    }
}
