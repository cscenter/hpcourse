import java.util.concurrent.atomic.AtomicMarkableReference

class LockFreeSetImpl<T : Comparable<T>> : LockFreeSet<T> {
    private val head = AtomicMarkableReference<Node<T>>(Head<T>(), false)

    private fun findPrev(value: T): AtomicMarkableReference<Node<T>> {
        var prev: AtomicMarkableReference<Node<T>> = head
        var cur: AtomicMarkableReference<Node<T>?> = head.reference.next
        while (cur.reference != null) {
            if (cur.isMarked) {
                val succ = cur.reference!!.next
                if (prev.compareAndSet(cur.reference, succ.reference, true, succ.isMarked))
                    cur = succ
            } else if (cur.reference!!.value >= value) {
                return prev
            }
            prev = cur as AtomicMarkableReference<Node<T>>
            cur = cur.reference.next
        }
        return prev
    }

    override fun add(value: T): Boolean {
        while (true) {
            val prev: AtomicMarkableReference<Node<T>> = findPrev(value)
            val next: AtomicMarkableReference<Node<T>?> = prev.reference.next

//          if persist and equal  --> false
            if (next.reference != null && !next.isMarked && next.reference!!.value == value) {
                return false
            }

            val newN = NodeImpl(value, next.reference)
            if (prev.reference.next.compareAndSet(next.reference, newN, next.isMarked, false))
                return true
        }
    }

    override fun remove(value: T): Boolean {
        while (true) {
            val prev = findPrev(value)
            val next = prev.reference.next.reference
            if (next == null || next.value != value) {
                return false
            }

            if (prev.reference.next.compareAndSet(next, next, false, true)) {
//              try to remove
                prev.reference.next.compareAndSet(next, next.next.reference, true, false)
                return true
            }
        }
    }

    override fun contains(value: T): Boolean {
        val prev = findPrev(value)
        return value == prev.reference.next.reference?.value
    }

    override fun isEmpty(): Boolean {
        var cur = head.reference.next
        while (cur.reference != null) {
            if (!cur.isMarked) return false
            cur = cur.reference!!.next
        }
        return true
    }

    // inner classes -----------------------------------

    private interface Node<T> {
        var next: AtomicMarkableReference<Node<T>?>
        val head: Boolean
        val value: T
    }

    private class Head<T>(next: Node<T>? = null) : Node<T> {
        override var next: AtomicMarkableReference<Node<T>?> = AtomicMarkableReference(next, false)
        override val head = true
        override val value
            get() = throw kotlin.UnsupportedOperationException()
    }

    private class NodeImpl<T>(override val value: T, next: Node<T>? = null) : Node<T> {
        override var next: AtomicMarkableReference<Node<T>?> = AtomicMarkableReference(next, false)
        override val head = false
    }
}