import java.util.concurrent.atomic.AtomicMarkableReference
import java.util.concurrent.atomic.AtomicReference

class LockFreeHashSet<T : Comparable<T>> : LockFreeSet<T> {
    private var head: AtomicReference<Node?> = AtomicReference(null)

    override fun add(value: T): Boolean {
        val key = value.hashCode()
        while (true) {
            val (prev, curr) = this.findNodes(key)
            val node = Node(key, value, AtomicMarkableReference(curr, false))

            // fail if value already in the set
            if (curr != null && curr.key == key) {
                return false
            }

            when (prev) {
            // if must insert before the head
            // or container is empty
                null -> {
                    // try to update the head
                    if (head.compareAndSet(curr, node)) {
                        return true
                    }
                }
            // if must insert after the tail
            // or somewhere between two elements
                else -> {
                    // try to add new element
                    val isCompareAndSetPassed = prev.nextAndIsDeletePair
                            .compareAndSet(curr, node, false, false)
                    if (isCompareAndSetPassed) {
                        return true
                    }
                }
            }
        }
    }

    override fun remove(value: T): Boolean {
        val key = value.hashCode()
        while (true) {
            val (prev, curr) = this.findNodes(key)

            // list is empty or there is no element with specified key
            if (curr == null || curr.key != key) {
                return false
            }

            val succ = curr.nextAndIsDeletePair.reference
            val isCASPassed = curr.nextAndIsDeletePair
                    .compareAndSet(succ, succ, false, true)
            if (isCASPassed) {  // curr is logically deleted
                when (prev) {
                    // if curr was the first element in the list
                    null -> {
                        // then try to move head one step forward
                        head.compareAndSet(curr, succ)
                    }
                    else -> {
                        // otherwise try to delete curr physically
                        prev.nextAndIsDeletePair
                                .compareAndSet(curr, succ, false, false)
                    }
                }
                return true
            }
        }
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

    private fun findNodes(
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
                    val isCompareAndSetPassed = pred!!.nextAndIsDeletePair
                            .compareAndSet(curr, succ, false, false)
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

    inner private class Node(
            val key: Int,
            val value: T,
            var nextAndIsDeletePair: AtomicMarkableReference<Node?>
    )

    override fun toString(): String {
        return buildString {
            var curr = head.get()
            while (curr != null) {
                append("${curr.value}->")
                curr = curr.nextAndIsDeletePair.reference
            }
            append("null\n")
        }
    }
}
