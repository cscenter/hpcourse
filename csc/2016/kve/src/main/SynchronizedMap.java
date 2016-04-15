package main;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class SynchronizedMap<K, V> {
    private final Map<K, V> myMap = new HashMap<>();
    private final Object monitor = new Object();

    public V get(K key) {
        synchronized (monitor) {
            return myMap.get(key);
        }
    }

    public void put(K key, V value) {
        synchronized (monitor) {
            myMap.put(key, value);
        }
    }

    public void remove(K key) {
        synchronized (monitor) {
            myMap.remove(key);
        }
    }

    public Set<K> getKeys() {
        synchronized (monitor) {
            return myMap.keySet();
        }
    }

    public Collection<V> getValues() {
        synchronized (monitor) {
            return myMap.values();
        }
    }
}
