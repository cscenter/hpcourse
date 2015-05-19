package com.cscenter;

/**
 * Created by Mark on 09.05.2015.
 */
public class SleepingTask implements Runnable {
    private final String name;
    private final int duration;

    public SleepingTask(String name, int duration){
        this.name = name;
        this.duration = duration;
    }

    @Override
    public void run() {
        System.out.println("Starting Task "+name+"....");
        try {
            Thread.sleep(duration*1000);
        } catch (InterruptedException e) {
            System.out.println("Exception in task " + name);
            System.out.println(e.getMessage());
        }
        System.out.println("Ended Task "+name+"....");
    }
}
