import java.util.concurrent.atomic.AtomicReference

class LockFreeSetImpl<T : Comparable<T>> : LockFreeSet<T> {
    private val head = Head<T>()

    private fun findPrev(value: T): Node<T> {
        var prev: Node<T> = head
        var cur: Node<T>? = head.next.get()
        while (cur != null) {
            if (cur.value >= value) {
                return prev
            }
            prev = cur
            cur = cur.next.get()
        }
        return prev
    }

    override fun add(value: T): Boolean {
        while (true) {
            val prev = findPrev(value)
            val next = prev.next.get()

            if (next != null && next.value == value) {
                return false
            }

            val node = NodeImpl(value, next)
            if (prev.next.compareAndSet(next, node)) return true
        }
    }

    override fun remove(value: T): Boolean {
        if (head.next.get() == null) return false

        while (true) {
            val prev = findPrev(value)
            val next = prev.next.get()
            if (next == null || next.value != value) {
                return false
            }

            if (prev.next.compareAndSet(next, next.next.get())) return true
        }
    }

    override fun contains(value: T): Boolean {
        val prev = findPrev(value)
        return value == prev.next.get()?.value
    }

    override fun isEmpty(): Boolean = head.next.get() == null

    // inner classes -----------------------------------

    private interface Node<T> {
        var next: AtomicReference<Node<T>?>
        val head: Boolean
        val value: T
    }

    private class Head<T>(next: Node<T>? = null) : Node<T> {
        override var next: AtomicReference<Node<T>?> = AtomicReference(next)
        override val head = true
        override val value
            get() = throw kotlin.UnsupportedOperationException()
    }

    private class NodeImpl<T>(override val value: T, next: Node<T>? = null) : Node<T> {
        override var next: AtomicReference<Node<T>?> = AtomicReference(next)
        override val head = false
    }
}