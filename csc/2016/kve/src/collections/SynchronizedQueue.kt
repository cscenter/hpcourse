package collections

import java.util.*

@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
class SynchronizedQueue<E> {
    private val queue = LinkedList<E>()

    @Synchronized fun put(item: E) {
        queue.add(item)
        if (queue.size == 1) {
            (this as Object).notify()
        }
    }

    @Synchronized @Throws(InterruptedException::class)
    fun pop(): E {
        while (queue.isEmpty()) {
            (this as Object).wait()
        }

        return queue.pop()
    }
}
