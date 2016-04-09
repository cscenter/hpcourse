package server;

import concurrent.MyAtomicInteger;
import concurrent.MyCallable;

/**
 * Created by dkorolev on 4/2/2016.
 */
public class TaskCallable implements MyCallable<Long> {
    private static final int PARAMETERS_COUNT = 5;
    public static enum ParamType {
        A, B, P, M, N
    }

    public final int taskId;

    private MyAtomicInteger countSet;
    private Long a;
    private Long b;
    private Long p;
    private Long m;
    private long n;

    public TaskCallable(int taskId) {
        this.taskId = taskId;
        countSet = new MyAtomicInteger();
    }

    @Override
    public Long call() throws Exception {
        while (n-- > 0)
        {
            b = (a * p + b) % m;
            a = b;
        }
        return a;
    }

    public boolean setParam(ParamType paramType, Long value) {
        switch (paramType) {
            case A:
                a = value;
                break;
            case B:
                b = value;
                break;
            case P:
                p = value;
                break;
            case M:
                m = value;
                break;
            case N:
                n = value;
                break;
        }

        return countSet.incrementAndGet() == PARAMETERS_COUNT;
    }
}
