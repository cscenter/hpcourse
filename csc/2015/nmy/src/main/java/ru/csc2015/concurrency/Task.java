package ru.csc2015.concurrency;

import java.util.concurrent.atomic.AtomicLong;

public class Task implements Runnable {
    private final Long id;
    private final Long duration;
    private static final AtomicLong generator = new AtomicLong();

    public Task(Long duration) {
        this.id = generator.getAndIncrement();
        this.duration = duration;
    }

    public void run() {
        System.out.println(toString() + " have started");
        try {
            Thread.sleep(1000L * this.duration);
        } catch (InterruptedException e) {
            System.out.println(toString() + " have been interrupted");
        }
        System.out.println(toString() + " done");
    }

    public Long getId() {
        return id;
    }

    @Override
    public String toString() {
        return "Task{" +
                "id=" + id +
                ", duration=" + duration +
                '}';
    }
}
