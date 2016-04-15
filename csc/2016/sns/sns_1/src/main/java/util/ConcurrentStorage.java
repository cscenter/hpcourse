package util;

import communication.Protocol;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Created by nikita.sokeran@gmail.com
 */
public class ConcurrentStorage<V> {

    private Map<Long, V> map = new HashMap<>();
    private long size = 0;

    private ReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * @param element
     * @return index of added element
     */
    public long add(final V value) {
        lock.writeLock().lock();
        try {
            final long index = size;
            map.put(size++, value);
            return index;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * @param id task id
     * @return task if exists or {@code null} if not
     */
    public V get(final long index) {
        lock.readLock().lock();
        try {
            return map.get(index);
        } finally {
            lock.readLock().unlock();
        }
    }
}
