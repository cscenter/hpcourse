package ru.compscicenter.ThreadPool;

/**
 * Created by Flok on 01.05.2015.
 */
public interface Task<V> {
    void run() throws Exception;
    void setInterruptionMonitor(InterruptionMonitor monitor);
    V getResult();
}
