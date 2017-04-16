package ru.compscicenter.ThreadPool;

/**
 * Created by Flok on 01.05.2015.
 */
public class AbstractTask implements Task {
    @Override
    public void run() throws Exception {

    }

    @Override
    public void setInterruptionMonitor(InterruptionMonitor monitor) {

    }

    @Override
    public Object getResult() {
        return null;
    }
}
