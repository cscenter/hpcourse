import java.util.concurrent.atomic.AtomicMarkableReference

class LockFreeSetImpl<T : Comparable<T>> : LockFreeSet<T> {
    private val head: Node<T> = Head()

    private fun findPrev(value: T): Node<T> {
        var prev: Node<T> = head
        var cur: Node<T>? = head.next
        retry@ while (cur != null) {
            if (prev.nextMarked) {
                val succ = cur.next
                if (!prev.removeNext()) {
                    continue@retry
                } else if (succ == null) {
                    return prev
                } else {
                    cur = succ
                }
            } else if (cur.value >= value) {
                return prev
            }
            prev = cur
            cur = cur.next
        }
        return prev
    }

    override fun add(value: T): Boolean {
        while (true) {
            val prev = findPrev(value)
            val next = prev.next

//          if persist and equal  --> false
            if (next != null && !prev.nextMarked && next.value == value) {
                return false
            }

            val newN = NodeImpl(value, next)
            if (prev.insert(newN)) {
                return true
            }
        }
    }

    override fun remove(value: T): Boolean {
        while (true) {
            val prev = findPrev(value)
            val next = prev.next
            if (next == null || next.value != value) {
                return false
            }

            if (prev.markNext(true)) {
//              try to remove
                prev.removeNext()
                return true
            }
        }
    }

    override fun contains(value: T): Boolean {
        val prev = findPrevWithoutRemove(value)
        return (value == prev.next?.value && !prev.nextMarked)
    }

    private fun findPrevWithoutRemove(value: T): Node<T> {
        var prev: Node<T> = head
        var cur: Node<T>? = head.next
        while (cur != null) {
            if (cur.value >= value) {
                return prev
            }
            prev = cur
            cur = cur.next
        }
        return prev
    }

    override fun isEmpty(): Boolean {
        var cur: Node<T> = head
        while (cur.next != null) {
            if (!cur.nextMarked) return false
            cur = cur.next!!
        }
        return true
    }

// inner classes -----------------------------------

    private interface Node<T> {
        val next: Node<T>?
        val value: T
        val nextMarked: Boolean
        val isHead: Boolean

        fun insert(node: Node<T>): Boolean
        fun markNext(flag: Boolean): Boolean
        fun removeNext(): Boolean
    }

    private abstract class AbstractNode<T>(next: Node<T>? = null) : Node<T> {
        private val ref: AtomicMarkableReference<Node<T>?> = AtomicMarkableReference(next, false)
        override val next: Node<T>?
            get() = ref.reference

        override val nextMarked: Boolean
            get() = ref.isMarked

        override fun insert(node: Node<T>): Boolean {
            val next = this.next
            return ref.compareAndSet(next, node, nextMarked, node.nextMarked)
        }

        override fun markNext(flag: Boolean): Boolean {
            return ref.compareAndSet(next, next, nextMarked, flag)
        }

        override fun removeNext(): Boolean {
//            if (!nextMarked) throw IllegalStateException("Can't remove the unmarked element")
            if (!nextMarked) return false
            val succ = next?.next
            return ref.compareAndSet(next, succ, true, succ?.nextMarked ?: false)
        }
    }

    private class Head<T>(next: Node<T>? = null) : AbstractNode<T>(next) {
        override val isHead = false
        override val value: T
            get() = throw UnsupportedOperationException("Head can not contain element")
    }

    private class NodeImpl<T>(override val value: T, next: Node<T>? = null) : AbstractNode<T>(next) {
        override val isHead: Boolean = false
    }
}