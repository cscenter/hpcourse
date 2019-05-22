import java.util.concurrent.atomic.AtomicMarkableReference
import java.util.concurrent.atomic.AtomicReference

const val NUM_THREADS = 3

class LockFreeSetImpl<T : Comparable<T>> : LockFreeSet<T> {
    private val head: Node
    private val snapPointer: AtomicReference<SnapCollector>

    inner class Node(val data: T?, next: Node?) {
        var state = AtomicMarkableReference(next, false)
        val next: Node?
            get() = state.reference

        fun setNext(next: Node) {
            state = AtomicMarkableReference(next, false)
        }

        fun isRemoved(): Boolean = state.isMarked

        fun markRemovedAttempt(forRemove: Node?) = state.compareAndSet(forRemove, forRemove, false, true)

        fun upgradeAttempt(oldChild: Node?, newChild: Node?) = state.compareAndSet(oldChild, newChild, false, false)
    }

    init {
        val tail = Node(null, null)
        head = Node(null, tail)
        tail.setNext(tail)

        val dummy = SnapCollector()
        dummy.blockFurtherReports()
        dummy.deactivate()
        snapPointer = AtomicReference(dummy)
    }

    inner class Window(val prev: Node, val curr: Node)

    private fun find(head: Node, data: T): Window {
        var prev: Node
        var curr: Node
        var succ: Node
        val marked = booleanArrayOf(false)
        var snip: Boolean
        retry@ while (true) {
            prev = head
            curr = prev.next!!
            while (true) {
                succ = curr.state.get(marked)!!
                while (marked[0]) {
                    val sc = snapPointer.get()
                    if (sc.isActive()) {
                        sc.report()
                    }
                    snip = prev.upgradeAttempt(curr, succ)
                    if (!snip) continue@retry
                    curr = succ
                    succ = curr.state.get(marked)!!
                }

                if (curr.data == null || curr.data!! >= data) {
                    return Window(prev, curr)
                }
                prev = curr
                curr = succ
            }
        }
    }

    override fun add(data: T): Boolean {
        var window: Window
        var prev: Node
        var curr: Node
        var node: Node
        while (true) {
            window = find(head, data)
            prev = window.prev
            curr = window.curr
            if (curr.data == data) {
                val sc = snapPointer.get()
                if (sc.isActive()) {
                    // report only if you are not going to be deleted
                    if (!curr.isRemoved()) sc.report()
                }
                return false
            } else {
                node = Node(data, curr)
                if (prev.upgradeAttempt(curr, node)) {
                    val sc = snapPointer.get()
                    if (sc.isActive()) {
                        // report only if you are not going to be deleted
                        if (!node.isRemoved()) sc.report()
                    }
                    return true
                }
            }
        }
    }

    override fun remove(data: T): Boolean {
        var snip: Boolean
        while (true) {
            val window = find(head, data)
            val prev = window.prev
            val curr = window.curr
            if (curr.data != data) {
                return false
            } else {
                val succ = curr.next
                // just marking the state pointer
                snip = curr.markRemovedAttempt(succ)
                if (!snip)
                    continue
                val sc = snapPointer.get()
                if (sc.isActive()) {
                    sc.report()
                }
                // physically removing curr
                prev.upgradeAttempt(curr, succ)
                return true
            }
        }
    }

    override fun contains(value: T): Boolean {
        return iterList().contains(value)
    }

    override fun isEmpty(): Boolean {
        return iterList().isEmpty()
    }

    override fun iterator(): Iterator<T> {
        return iterList().iterator()
    }

    inner class SnapCollector {
        private val reports: Array<ReportItem?>
        private val tail: AtomicReference<NodeWrapper>
        private val blocker = ReportItem()
        @Volatile
        private var active = false

        private val allReports = AtomicReference<Array<ReportItem>>(null)

        inner class NodeWrapper(var node: Node? = null, var data: T? = null) {
            val next = AtomicReference<NodeWrapper>(null)
        }

        init {
            val head = NodeWrapper(data = null)
            tail = AtomicReference(head)
            active = true

            reports = arrayOfNulls(NUM_THREADS)
            for (i in 0 until NUM_THREADS) {
                reports[i] = ReportItem()
            }
        }

        fun addNode(node: Node, data: T?): Node? {
            val last = tail.get()
            if (last.data == null || last.data!! >= data!!) {
                return last.node
            }

            if (last.next.get() != null) {
                if (last === tail.get())
                    tail.compareAndSet(last, last.next.get())
                return tail.get().node
            }

            val newNode = NodeWrapper(node, data)
            return if (last.next.compareAndSet(null, newNode)) {
                tail.compareAndSet(last, newNode)
                node
            } else {
                tail.get().node
            }
        }

        fun report() {
            val threadId = 0
            val tail = reports[threadId]
            val newItem = ReportItem()
            if (tail!!.next.compareAndSet(null, newItem))
                reports[threadId] = newItem
        }

        fun isActive(): Boolean {
            return active
        }

        fun blockFurtherPointers() {
            val blocker = NodeWrapper(null, null)
            tail.set(blocker)
        }

        fun deactivate() {
            active = false
        }

        fun blockFurtherReports() {
            for (i in 0 until NUM_THREADS) {
                val tail = reports[i]
                if (tail!!.next.get() == null)
                    tail.next.compareAndSet(null, blocker)
            }
        }

        fun getReports(): List<ReportItem> {
            return allReports.get()?.filter { it.data != null } ?: return emptyList()
        }
    }

    private fun getSnapshot(): SnapCollector {
        val sc = acquireSnapCollector()
        collectSnapshot(sc)
        return sc
    }

    private fun acquireSnapCollector(): SnapCollector {
        var result: SnapCollector
        result = snapPointer.get()
        if (!result.isActive()) {
            val candidate = SnapCollector()
            result = if (snapPointer.compareAndSet(result, candidate))
                candidate
            else
                snapPointer.get()
        }
        return result
    }

    private fun collectSnapshot(sc: SnapCollector) {
        var curr: Node? = head.next
        while (sc.isActive()) {
            if (!curr!!.isRemoved())
                curr = sc.addNode(curr, curr.data)
            if (curr == null) {
                sc.blockFurtherPointers()
                sc.deactivate()
                break
            }
            if (curr.data == null) {
                sc.blockFurtherPointers()
                sc.deactivate()
            }
            curr = curr.next
        }
        sc.blockFurtherReports()
    }

    private fun iterList(): List<T> {
        val snap = getSnapshot()
        return snap.getReports().map { it.data!! }
    }

    inner class ReportItem(val data: T? = null) {
        val next: AtomicReference<ReportItem> = AtomicReference<ReportItem>(null)
    }
}