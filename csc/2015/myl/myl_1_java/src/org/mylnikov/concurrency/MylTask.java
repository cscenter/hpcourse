package org.mylnikov.concurrency;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by alex on 5/13/2015.
 */
public class MylTask implements Runnable {
    private final int durationS;
    static private AtomicInteger i = new AtomicInteger(1);
    private final long id;

    @Override
    public void run() {
        System.out.println("Task for sleep id=" + id + durationS / 10000.0 + " s.");
        try {
            Thread.sleep(durationS);
        } catch (InterruptedException e) {
            System.out.println("end exeption" +id);
        }
        System.out.println("Task end id = " + id);
    }

    MylTask(int durationInSeconds) {
        this.id = i.getAndIncrement();
        this.durationS = durationInSeconds;
    }


    public long getId() {
        return id;
    }

}