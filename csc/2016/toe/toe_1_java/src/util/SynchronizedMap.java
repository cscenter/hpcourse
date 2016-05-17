package util;

import java.util.*;
import java.util.stream.Collectors;

public class SynchronizedMap<K, V> {
    private final HashMap<K, V> map;

    public SynchronizedMap() {
        map = new HashMap<>();
    }

    public V get(K key) {
        V value;
        synchronized (map) {
            value = map.get(key);
        }
        return value;
    }

    public void put(K key, V value) {
        synchronized (map) {
            map.put(key, value);
        }
    }

    public List<V> getValues() {
        ArrayList<V> res = new ArrayList<>();
        synchronized (map) {
            res.addAll(map.keySet().stream().map(map::get).collect(Collectors.toList()));
        }
        return res;
    }
}
