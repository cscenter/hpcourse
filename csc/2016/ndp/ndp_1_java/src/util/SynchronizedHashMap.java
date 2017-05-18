package util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class SynchronizedHashMap<K, V> {
    private final HashMap<K, V> map;

    public SynchronizedHashMap() {
        this.map = new HashMap<>();
    }

    public V get(K key) throws InterruptedException {
        if (key == null) {
            throw new NullPointerException();
        }
        synchronized (map) {
            while (!map.containsKey(key)) {
                map.wait();
            }
            return this.map.get(key);
        }
    }

    public void put(K key, V value) throws InterruptedException {
        if (key == null || value == null) {
            throw new NullPointerException();
        }
        synchronized (map) {
            map.put(key, value);
            map.notifyAll();
        }
    }

    public Set<K> keySet() {
        HashSet<K> set = new HashSet<>();
        synchronized (this) {
            this.map.keySet().forEach(set::add);
        }
        return set;
    }

    public boolean containsKey(K key) {
        synchronized (this) {
            return this.map.containsKey(key);
        }
    }
}
