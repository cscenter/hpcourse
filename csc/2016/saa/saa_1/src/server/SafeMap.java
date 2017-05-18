package server;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by andrey on 15.04.16.
 */
public class SafeMap<K, V> {
    Map<K, V> map = new HashMap<>();

    SafeMap() {}

    public V get(K key) {
        synchronized (this) {
            return map.get(key);
        }
    }

    public V put(K key, V value) {
        synchronized (this) {
            return map.put(key, value);
        }
    }

    public synchronized boolean containsKey(K key) {
        return map.containsKey(key);
    }

    public void remove(K key) {
        synchronized(this) {
            map.remove(key);
        }
    }

    public Collection<V> values() {
        synchronized (this) {
            return map.values();
        }
    }
}
