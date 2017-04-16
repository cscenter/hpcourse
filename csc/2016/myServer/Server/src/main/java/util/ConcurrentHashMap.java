package util;

import java.util.HashMap;
import java.util.Set;

public class ConcurrentHashMap<K, V> {

    private final HashMap<K, V> hashMap;

    public ConcurrentHashMap(int capacity) {
        hashMap =  new HashMap<K, V>(capacity);
    }

    public ConcurrentHashMap() {
        this(100);
    }

    public synchronized V get (K key) {
        return hashMap.get(key);
    }

    public synchronized V put (K key, V value) {
        return hashMap.put(key, value);
    }

    public synchronized boolean containsKey(K key) {
        return hashMap.containsKey(key);
    }

    public synchronized Set<K> keySet(){
        return hashMap.keySet();
    }

}
