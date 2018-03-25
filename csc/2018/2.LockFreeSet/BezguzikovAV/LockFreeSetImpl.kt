package ex

import java.util.concurrent.atomic.AtomicStampedReference

class LockFreeSetImpl<in T : Comparable<T>> : LockFreeSet<T> {

    private val head = SimpleNode<T>(null)

    override fun add(value: T): Boolean {
        traverse(value) { next ->
            if (next != null && next.obj == value && !next.isRemoved()) {
                return false
            }

            if (!addAttempt(next, Node(value, next))) {
                return add(value)
            }
        }
        return true
    }

    override fun remove(value: T): Boolean {
        traverse(value) { next ->
            if (next != null && next.obj == value) {
                if (!next.markRemovedAttempt(next.next)) {
                    return remove(value)
                }
                return true
            }
        }
        return false
    }

    override fun isEmpty(): Boolean {
        return head.next == null
    }

    override fun contains(value: T): Boolean {
        traverse(value) { next ->
            if (next != null && next.obj == value) {
                return true
            }
        }
        return false
    }

    private inline fun traverse(value: T, action: SimpleNode<T>.(Node<T>?) -> Unit) {

        var current = head
        var next = current.next

        while (next != null && next.obj < value) {
            if (next.isRemoved()) {
                if (!current.removeAttempt(next)) {
                    current = head
                    next = current.next
                }
                continue
            }
            current = current.next as SimpleNode<T>
            next = next.next
        }
        current.action(next)
    }
}

private open class SimpleNode<T : Comparable<T>>(next: Node<T>?) {
    var state = AtomicStampedReference(next, 0)

    val next: Node<T>?
        get() = state.reference

    fun isRemoved(): Boolean = state.stamp != 0

    fun markRemovedAttempt(forRemove: Node<T>?)
            = state.compareAndSet(forRemove, forRemove, 0, 1)

    fun removeAttempt(forRemove: Node<T>)
            = state.compareAndSet(forRemove, forRemove.next, 0, 0)

    fun addAttempt(oldChild: Node<T>?, newChild: Node<T>)
            = state.compareAndSet(oldChild, newChild, 0, 0)
}

private class Node<T : Comparable<T>>(val obj: T, next: Node<T>?) : SimpleNode<T>(next)
