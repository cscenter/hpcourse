package ru.compscicenter.ThreadPool.TestTasks;

import ru.compscicenter.ThreadPool.AbstractTask;
import sun.net.ConnectionResetException;

/**
 * Created by Flok on 02.05.2015.
 */
public class ExceptionTask extends AbstractTask {
    @Override
    public void run() throws ConnectionResetException {
        throw new ConnectionResetException();
    }
}
