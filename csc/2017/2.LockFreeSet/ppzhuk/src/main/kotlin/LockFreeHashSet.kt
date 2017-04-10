import java.util.concurrent.atomic.AtomicMarkableReference
import java.util.concurrent.atomic.AtomicReference

class LockFreeHashSet<T : Comparable<T>> : LockFreeSet<T> {
    /*private*/ var head: AtomicReference<Node?> = AtomicReference(null)

    override fun add(value: T): Boolean {
        val key = value.hashCode()
        retry@
        while (true) {
            val (prev, curr) = this.findNodes(key)
            val node = Node(key, value, AtomicMarkableReference(curr, false))

            when {
            // if container is empty
                prev == null && curr == null -> {
                    // try to update the head
                    if (head.compareAndSet(null, node)) {
                        return true
                    }
                    continue@retry
                }
            // if must insert before the head
                prev == null && curr != null -> {
                    if (curr.key == key) {
                        return false
                    } else {
                        if (head.compareAndSet(curr, node)) {
                            return true
                        }
                        continue@retry
                    }
                }
            // if must insert after the tail
            // or somewhere between two elements
                else -> {
                    if (curr != null && curr.key == key) {
                        return false
                    } else {
                        val isCompareAndSetPassed = prev!!.nextAndIsDeletePair.
                                compareAndSet(curr, node, false, false)
                        if (isCompareAndSetPassed) {
                            return true
                        }
                        continue@retry
                    }
                }
            }
        }
    }

    override fun remove(value: T): Boolean {
        TODO()
    }

    override fun contains(value: T): Boolean {
        val key = value.hashCode()
        var curr = head.get()
        while (curr != null && curr.key < key) {
            curr = curr.nextAndIsDeletePair.reference
        }
        return curr != null && curr.key == key && !curr.nextAndIsDeletePair.isMarked
    }

    override fun isEmpty() =
            head.get() == null

    /*private*/ fun findNodes(
            key: Int
    ): Pair<Node?, Node?> {
        retry@
        while (true) {
            var pred = head.get()
            var curr = pred?.nextAndIsDeletePair?.reference

            when {
            // if list is empty
                pred == null -> {
                    return null to null
                }
            // if head is logically deleted
                pred.nextAndIsDeletePair.isMarked -> {
                    // then trying to move head one step forward
                    head.compareAndSet(pred, pred.nextAndIsDeletePair.reference)
                    // retry despite the result
                    continue@retry
                }
            // if we want to delete the head element or insert before the head
                pred.key >= key -> {
                    return null to pred
                }
            }

            var succ: Node?
            while (true) {
                if (curr == null) {
                    return pred to curr
                }
                succ = curr.nextAndIsDeletePair.reference
                val isCurrDeleted = curr.nextAndIsDeletePair.isMarked
                if (isCurrDeleted) { // curr is logically deleted
                    // then trying to delete curr physically
                    val isCompareAndSetPassed = pred!!.nextAndIsDeletePair.
                            compareAndSet(curr, succ, false, false)
                    if (!isCompareAndSetPassed) {
                        continue@retry
                    }
                    curr = succ
                } else {
                    if (curr.key >= key) {
                        return pred to curr
                    }
                    pred = curr
                    curr = succ
                }
            }
        }
    }

    inner /*private*/ class Node(
            val key: Int,
            val value: T,
            var nextAndIsDeletePair: AtomicMarkableReference<Node?>
    )
}

