package collections

import java.util.*

class SynchronizedMap<K, V> {
    private val myMap = HashMap<K, V>()
    private val monitor = Object()

    @Synchronized operator fun get(key: K): V? {
        return myMap[key]
    }

    @Synchronized fun put(key: K, value: V) {
        myMap.put(key, value)
    }

    fun remove(key: K) {
        synchronized (monitor) {
            myMap.remove(key)
        }
    }

    val keys: Set<K>
        @Synchronized get() = myMap.keys

    val values: Collection<V>
        @Synchronized get() = myMap.values
}
