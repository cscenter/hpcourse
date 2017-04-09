import java.util.concurrent.atomic.AtomicMarkableReference
import java.util.concurrent.atomic.AtomicReference

class LockFreeHashSet<T : Comparable<T>> : LockFreeSet<T> {
    /*private*/ var head: AtomicReference<Node?> = AtomicReference(null)

    override fun add(value: T): Boolean {
        TODO()
    }

    override fun remove(value: T): Boolean {
        TODO()
    }

    override fun contains(value: T): Boolean {
        TODO()
    }

    override fun isEmpty(): Boolean {
        TODO()
    }

    /*private*/ fun findNodes(
            key: Int
    ): Pair<Node?, Node?> {
        retry@
        while (true) {
            var pred = head.get()
            var curr = pred?.nextAndIsDeletePair?.reference

            when {
                pred == null -> {
                    return null to null
                }
                // if we want to delete the head element or insert before the head
                pred.key >= key -> {
                    return null to pred
                }
                // if head is logically deleted
                pred.nextAndIsDeletePair.isMarked -> {
                    // then trying to move head one step forward
                    head.compareAndSet(pred, pred.nextAndIsDeletePair.reference)
                    // retry despite the result
                    continue@retry
                }
                curr == null -> {
                    return pred to null
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
                    val isCompareAndSetPasses = pred!!.nextAndIsDeletePair.
                            compareAndSet(curr, succ, false, false)
                    if (!isCompareAndSetPasses) {
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

    inner class Node(
            val key: Int,
            val value: T,
            var nextAndIsDeletePair: AtomicMarkableReference<Node?>
    )
}

