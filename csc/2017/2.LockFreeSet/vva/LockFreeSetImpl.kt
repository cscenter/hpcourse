import java.util.concurrent.atomic.AtomicReference

/**
 * Created by vit-vel on 09.04.17.
 */

/**
 * The implementation of lock-free set as lock-free ordered list
 * Kotlin v1.1
 */
class LockFreeSetImpl<T : Comparable<T>> : LockFreeSet<T> {
    private var head: AtomicReference<ImmutableNode<T>?> = AtomicReference()

    override fun add(value: T): Boolean {
        while (true) {
            val (prev, curr) = findContext(value)
            val previousNode = prev.get()
            val currentNode = curr.get()

            if (value == currentNode?.data) {
                if (currentNode.isDeleted) {
                    // if the value logically removed, but isn't pysically excluded from the list, then try to add logically
                    if (curr.compareAndSet(currentNode, currentNode.copy(isDeleted = false))) {
                        return true
                    }
                } else {
                    return false
                }
            } else {
                // if the value should be insert as the head of list, try to do it
                // else try to insert in the correct position
                if (previousNode == null) {
                    if (head.compareAndSet(currentNode, ImmutableNode(value, AtomicReference(curr.get())))) {
                        return true
                    }
                } else {
                    if (previousNode.next.compareAndSet(currentNode, ImmutableNode(value, AtomicReference(curr.get())))) {
                        return true
                    }
                }
            }
        }
    }

    override fun remove(value: T): Boolean {
        // mark node as removed
        while (true) {
            val (prev, curr) = findContext(value)
            val currentNode = curr.get()

            if (currentNode == null || currentNode.data != value) return false

            if (curr.compareAndSet(currentNode, currentNode.copy(isDeleted = true))) {
                break
            }
        }

        return trueRemove(value)
    }

    override fun contains(value: T): Boolean {
        var currentNode = head.get()

        while (currentNode != null && currentNode.data < value) {
            currentNode = currentNode.next.get()
        }
        return value == currentNode?.data
    }

    // The unfair implementation.
    // if all values are marked as removed but physically haven't excludet yet,
    // then false will be returned, but the true must be
    override fun isEmpty() = head.get() == null

    /**
     * return pair of nodes:
     * 1. Node with the maximum data < value or null if there is no such node
     * 2. Node with the minimum data >= value or null if there is no such node
     */
    private fun findContext(value: T): Pair<AtomicReference<ImmutableNode<T>?>, AtomicReference<ImmutableNode<T>?>> {
        var prev = head
        var curr = head.get()?.next ?: AtomicReference<ImmutableNode<T>?>()

        var prevNode = prev.get()

        if (prevNode != null && prevNode.data >= value) {
            return Pair(AtomicReference(), prev)
        }

        var currentNode = curr.get()
        while (currentNode != null && currentNode.data < value) {
            prev = curr
            curr = currentNode.next
            currentNode = curr.get()
        }

        return Pair(prev, curr)
    }

    private fun trueRemove(value: T): Boolean {
        while (true) {
            val (prev, curr) = findContext(value)
            val currentNode = curr.get()

            if (currentNode == null || currentNode.data != value) {
                return false
            }

            if (!currentNode.isDeleted) {
                // node was removed but then inserted again by someone else
                return true
            }

            val prevNode = prev.get()

            // if the removable item is head, try to {head = curr.next}
            // else try to {prev.next = curr.next}
            if (prevNode == null) {
                if (head.compareAndSet(currentNode, currentNode.next.get())) {
                    return true
                }
            } else {
                if (prevNode.next.compareAndSet(currentNode, currentNode.next.get())) {
                    return true
                }
            }
        }
    }

    private data class ImmutableNode<T : Comparable<T>> (val data: T, val next: AtomicReference<ImmutableNode<T>?> = AtomicReference(null), val isDeleted: Boolean = false) {
        // compare by pointer
        override fun equals(other: Any?): Boolean {
            return super.equals(other)
        }
    }
}
