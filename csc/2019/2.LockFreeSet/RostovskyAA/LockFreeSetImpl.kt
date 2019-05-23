import java.util.concurrent.atomic.AtomicMarkableReference
import java.util.concurrent.atomic.AtomicReference

const val NUM_THREADS = 3

class LockFreeSetImpl<T : Comparable<T>> : LockFreeSet<T> {
    private val head: Node
    private val snapPointer: AtomicReference<SnapCollector>

    enum class ReportType {
        INSERT,
        DELETE
    }

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

    private fun find(data: T): Window {
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
                    reportDelete(curr)
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
        var previous: Node
        var current: Node
        var node: Node
        while (true) {
            find(data).apply {
                previous = prev
                current = curr
            }
            if (current.data == data) {
                reportInsert(current)
                return false
            }
            else {
                node = Node(data, current)
                if (previous.upgradeAttempt(current, node)) {
                    reportInsert(node)
                    return true
                }
            }
        }
    }

    fun reportInsert(node: Node){
        // addReport INSERT only if you are not going to be deleted
        if (node.isRemoved()) return

        val sc = snapPointer.get()
        sc.addReport(node, ReportType.INSERT)
    }

    fun reportDelete(node: Node) {
        val sc = snapPointer.get()
        sc.addReport(node, ReportType.DELETE)
    }

    override fun remove(data: T): Boolean {
        var snip: Boolean
        while (true) {
            val previous: Node
            val current: Node
            find(data).apply {
                previous = prev
                current = curr
            }

            if (current.data != data) {
                return false
            } else {
                val next = current.next
                // just marking the state pointer
                snip = current.markRemovedAttempt(next)
                if (!snip) continue
                reportDelete(current)
                // physically removing curr
                previous.upgradeAttempt(current, next)
                return true
            }
        }
    }

    override fun contains(data: T): Boolean {
        val current: Node
        find(data).apply {
            current = curr
        }
        if ( current.data != data || current.isRemoved()) return false
        return true
    }

    override fun isEmpty(): Boolean {
        var current = head.next
        while (current != null && current.isRemoved()) {
            current = current.next
        }
        return current == null
    }

    override fun iterator(): Iterator<T> = getSnapshot().iterator()

    inner class SnapCollector {
        private val reports = mutableMapOf<Int, NodeWrapper>()
        private val head = NodeWrapper()
        private val tail = AtomicReference(head)
        private val blocker = NodeWrapper()
        @Volatile
        private var active = true

        inner class NodeWrapper(var node: Node? = null,
                                val type: ReportType? = null) {
            val next = AtomicReference<NodeWrapper>(null)
        }

        fun addNode(node: Node): Node? {
            val last = tail.get()

            val lastNode = last.node
            if (lastNode == null || (lastNode.data != null && node.data != null && lastNode.data >= node.data)) {
                return lastNode
            }

            if (last.next.get() != null) {
                if (last === tail.get()) tail.compareAndSet(last, last.next.get())
                return tail.get().node
            }

            val newNode = NodeWrapper(node)
            return if (last.next.compareAndSet(null, newNode)) {
                tail.compareAndSet(last, newNode)
                node
            } else {
                tail.get().node
            }
        }

        fun addReport(victim: Node, action: ReportType) {
            if (!isActive()) return

            val threadId = Thread.currentThread().id.toInt()
            val tail = reports.getOrPut(threadId) { NodeWrapper() }
            val newItem = NodeWrapper(victim, action)

            if (tail == blocker) return
            if (tail.next.compareAndSet(null, newItem)) {
                reports[threadId] = newItem
            }
        }

        fun isActive(): Boolean {
            return active
        }

        fun blockFurtherPointers() {
            tail.set(blocker)
        }

        fun deactivate() {
            active = false
        }

        fun blockFurtherReports() {
            reports.values.forEach { tail ->
                if (tail.next.get() == null) {
                    tail.next.compareAndSet(null, blocker)
                }
            }
        }

        fun getNodes() : List<Node> {
            val result = arrayListOf<Node>()
            var current: NodeWrapper? = head
            while (true) {
                current = current?.next?.get()
                if (current == null) break
                if (current.node == null) break
                result.add(current.node!!)
            }
            return result
        }

        fun getReports(reportType: ReportType) : List<Node> {
            val result = arrayListOf<Node>()
            var current: NodeWrapper?
            for (report in reports.values) {
                current = report
                while (current != null) {
                    val node = current.node
                    if (node != null && current.type == reportType) result.add(node)
                    current = current.next.get()
                }
            }
            return result
        }
    }

    private fun reconstructUsingReports(sc: SnapCollector) : ArrayList<T> {
        val snapshot = mutableListOf<Node>()
        val inserted = sc.getReports(ReportType.INSERT)
        val deleted = sc.getReports(ReportType.DELETE)
        val nodes = sc.getNodes()
        snapshot.addAll(inserted)
        snapshot.addAll(nodes)
        snapshot.removeAll(deleted)
        val result = arrayListOf<T>()
        for (node in snapshot) {
            if (node.data != null) result.add(node.data)
        }
        return result
    }

    private fun getSnapshot(): List<T> {
        val sc = acquireSnapCollector()
        collectSnapshot(sc)
        return reconstructUsingReports(sc)
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
                curr = sc.addNode(curr)
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
}