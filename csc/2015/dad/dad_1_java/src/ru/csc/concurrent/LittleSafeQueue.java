package ru.csc.concurrent;

import java.util.LinkedList;
import java.util.NoSuchElementException;

public class LittleSafeQueue extends SafeQueue {
    private final LinkedList<Integer> unsafeQueue = new LinkedList<>();

    public boolean isEmpty() {
        try {
            synchronized (unsafeQueue.getFirst()) {
                return unsafeQueue.isEmpty();
            }
        } catch (NoSuchElementException e) {
            return true;
        }
    }

    @Override
    public boolean push(Integer taskID) {
        try {
            synchronized (unsafeQueue.getLast()) {
                unsafeQueue.addLast(taskID);
                return false;
            }
        } catch (NoSuchElementException e) {
            synchronized (unsafeQueue) {
                unsafeQueue.addLast(taskID);
                return true;
            }
        }
    }

    @Override
    public Integer pop() {
        try {
            synchronized (unsafeQueue.getFirst()) {
                return unsafeQueue.pollFirst();
            }
        } catch (NoSuchElementException e) {
            //System.out.println("Empty");
            return null;
        }
    }
}