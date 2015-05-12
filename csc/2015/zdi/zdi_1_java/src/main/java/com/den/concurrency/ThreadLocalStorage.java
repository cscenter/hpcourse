package com.den.concurrency;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Zaycev Denis
 */
public abstract class ThreadLocalStorage<T> {

    private Map<Thread, T> values = new HashMap<Thread, T>();

    public T get() {
        Thread current = Thread.currentThread();

        T value = values.get(current);
        if (value == null) {
            values.put(current, value = getInitialValue());
        }

        return value;
    }

    public abstract T getInitialValue();

}
