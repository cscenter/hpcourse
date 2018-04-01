import java.util.concurrent.atomic.AtomicStampedReference

class LockFreeSetImpl<T: Comparable<T>> : LockFreeSet<T> {

    private val head = Node<T>(null, null)

    override fun add(value: T): Boolean {
        while (true) {
            val (prev, current) = findInterval(value)

            if (current?.value == value) {
                return false
            }

            val success = if (prev == null) {
                val node = Node(value, current)
                head.nextNode.compareAndSet(null, node, 0, 0)
            }
            else {
                val node = Node(value, current)
                prev.nextNode.compareAndSet(current, node, 0, 0)
            }

            if (success) {
                return true
            }
        }
    }

    override fun remove(value: T): Boolean {
        while (true) {
            val (prev, current) = findInterval(value)

            if (current == null || current.value != value) {
                return false
            }

            val successMark = current.nextNode.compareAndSet(current.nextNode.reference, current.nextNode.reference, 0, 1)

            if (!successMark) {
                continue
            }

            //remove any way
            while (true) {
                val successDelete = prev!!.nextNode.compareAndSet(current, current.nextNode.reference, 0, 0)

                if (successDelete) {
                    return true
                }
            }

        }
    }

    override fun contains(value: T): Boolean {
        val (current, _) = findInterval(value);
        return current?.value == value && current.nextNode.stamp == 0
    }

    override fun isEmpty(): Boolean {
        return head.nextNode.reference == null
    }

    class Node<T: Comparable<T>>(val value: T?, next: Node<T>?) {
        var nextNode: AtomicStampedReference<Node<T>?> = AtomicStampedReference(next, 0)
    }

    data class NodeInterval<T: Comparable<T>>(val prev: Node<T>?, val next: Node<T>?)

    private fun findInterval(value: T): NodeInterval<T> {
        var prev : Node<T>? = null
        var current: Node<T>? = head
        while (current != null && (current.value == null || current.value!! < value)) {
            prev = current
            current = current.nextNode.reference
        }
        return NodeInterval(prev, current)
    }



}