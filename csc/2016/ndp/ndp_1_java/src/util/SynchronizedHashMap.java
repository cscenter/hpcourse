package util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class SynchronizedHashMap<K, V> {
    private final HashMap<K, V> map;

    public SynchronizedHashMap() {
        map = new HashMap<>();
    }

    public V get(K key) throws InterruptedException {
        if (key == null) {
            throw new IllegalArgumentException("key must be not null");
        }
        final V value;
        synchronized (map) {
            if (!map.containsKey(key)) {
                throw new RuntimeException(String.format("Map does not contains key %s", key.toString()));
            }
            value = map.get(key);
            map.notify();
        }
        return value;
    }

    public void put(K key, V value) throws InterruptedException {
        if (key == null || value == null) {
            throw new IllegalArgumentException("key and value must be not null");
        }
        synchronized (map) {
            map.put(key, value);
            map.notify();
        }
    }

    public Set<K> keySet() {
        HashSet<K> set = new HashSet<>();
        synchronized (map) {
            map.keySet().forEach(set::add);
        }
        return set;
    }

    public boolean containsKey(K key) {
        boolean contains;
        synchronized (map) {
            contains = map.containsKey(key);
        }
        return contains;
    }
}
