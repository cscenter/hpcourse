package ru.compscicenter.hpc2016.ha1.util;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class SynchronizedHashMap<K, V> {

    private HashMap<K, V> hashMap;

    public SynchronizedHashMap() {
        hashMap = new HashMap<>();
    }

    public SynchronizedHashMap(int initialCapacity) {
        hashMap = new HashMap<>(initialCapacity);
    }

    public synchronized int size() {
        return hashMap.size();
    }

    public synchronized boolean isEmpty() {
        return hashMap.isEmpty();
    }

    public synchronized V get(Object key) {
        return hashMap.get(key);
    }

    public synchronized boolean containsKey(Object key) {
        return hashMap.containsKey(key);
    }

    public synchronized boolean containsValue(Object value) {
        return hashMap.containsValue(value);
    }

    public synchronized V put(K key, V value) {
        return hashMap.put(key, value);
    }

    public synchronized V remove(Object key) {
        return hashMap.remove(key);
    }

    public synchronized void putAll(Map<? extends K, ? extends V> map) {
        hashMap.putAll(map);
    }

    public synchronized void clear() {
        hashMap.clear();
    }

    public synchronized Set<K> keySet() {
        return hashMap.keySet();
    }

    public synchronized Set<Map.Entry<K, V>> entrySet() {
        return hashMap.entrySet();
    }

    public synchronized Collection<V> values() {
        return hashMap.values();
    }

    public synchronized boolean equals(Object o) {
        return this == o || hashMap.equals(o);
    }

    public synchronized int hashCode() {
        return hashMap.hashCode();
    }

    public synchronized String toString() {
        return hashMap.toString();
    }

}
