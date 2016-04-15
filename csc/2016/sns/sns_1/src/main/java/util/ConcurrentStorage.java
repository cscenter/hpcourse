package util;

import javafx.util.Pair;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * Created by nikita.sokeran@gmail.com
 */
public class ConcurrentStorage<V> {

    private Map<Long, V> map = new HashMap<>();
    private long size = 0;

    private ReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * @param value element value
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
     * @param index index of wanted element
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

    public List<Pair<Long, V>> getContent() {
        return map.entrySet().stream().map(e -> new Pair<>(e.getKey(), e.getValue())).collect(Collectors.toList());
    }
}
