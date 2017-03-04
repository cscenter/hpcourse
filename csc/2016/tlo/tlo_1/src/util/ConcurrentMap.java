package util;

import java.util.Collection;
import java.util.HashMap;

/**
 * Created by lt on 12.05.16.
 */



public class ConcurrentMap<K, V> {

    private HashMap<K, V> map = new HashMap<>();


    public synchronized boolean containsKey(K key) {
        return map.containsKey(key);
    }

    public synchronized V get(K key) {
        return map.get(key);
    }

    public synchronized void put(K key, V value) {
        map.put(key, value);
    }


    public synchronized void remove(K key) {
        map.remove(key);
    }


    public synchronized int size() {
        return map.size();
    }

    public synchronized Collection<V> values() {
        return map.values();
    }
}
