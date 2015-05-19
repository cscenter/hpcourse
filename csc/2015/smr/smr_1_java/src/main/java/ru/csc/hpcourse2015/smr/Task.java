package ru.csc.hpcourse2015.smr;

import java.util.concurrent.atomic.AtomicInteger;

public class Task implements Runnable{

    private final Integer id = IDCreator.giveID();
    private final Integer duration;

    public Task(Integer duration) {
        this.duration = duration;
    }

    public Integer getId() {
        return id;
    }

    @Override
    public void run() {
        System.out.println("Task with id: " + id + " is going to sleep for " + duration + " sec.");
        try {
            Thread.sleep(duration * 1000);

        } catch (InterruptedException exc) {
            System.out.println("Task with id: " + id + " was interrupted.");
        }
        System.out.println("Task with id: " + id  + " end successfully!");
    }


    public static class IDCreator {
        private static AtomicInteger id = new AtomicInteger();

        public static Integer giveID() {
            return id.getAndIncrement();
        }
    }
}
