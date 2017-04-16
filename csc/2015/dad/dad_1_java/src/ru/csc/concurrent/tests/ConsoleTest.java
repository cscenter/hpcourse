package ru.csc.concurrent.tests;

import ru.csc.concurrent.LittleTask;
import ru.csc.concurrent.MyLittleThreadPool;

public class ConsoleTest {
    public static void main(String[] args) throws InterruptedException {
        MyLittleThreadPool<Void> pool = new MyLittleThreadPool<>(2);
        pool.submit(LittleTask.createTimedTask(5));
        pool.submit(LittleTask.createTimedTask(10));
        pool.submit(LittleTask.createTimedTask(3));
        try {
            Thread.sleep(20000);
        } catch (InterruptedException e) {
            throw new InterruptedException("Oops");
        }
        pool.submit(LittleTask.createTimedTask(5));
        pool.submit(LittleTask.createTimedTask(10));
        pool.submit(LittleTask.createTimedTask(3));
        System.out.println("some");
    }
}
