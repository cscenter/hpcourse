import java.util.concurrent.atomic.AtomicMarkableReference
import java.util.concurrent.atomic.AtomicReference
import java.util.ArrayList

class LockFreeSetImpl<T: Comparable<T>> : LockFreeSet<T> {

    private val head = Node<T>(null, null)
    private val psc : AtomicReference<SnapCollector<T>>

    enum class ReportType {
        INSERTED,
        DELETED,
        DUMMY
    }

    init {
        val sc = SnapCollector<T>()
        sc.deactivate()
        psc = AtomicReference(sc)
    }

    override fun add(value: T): Boolean {
        while (true) {
            val (pred, current) = find(value)
            if (current?.value == value) return false
            val node = Node(value, current)
            if (pred.nextNode.compareAndSet(current, node, false, false)) return true
        }
    }

    override fun remove(value: T): Boolean {
        while (true) {
            val (pred, current) = find(value)
            if (current == null || current.value != value) return false
            if (!current.nextNode.compareAndSet(current.nextNode.reference, current.nextNode.reference, false, true))  {
                continue
            }
            reportDelete(current)
            pred.nextNode.compareAndSet(current, current.nextNode.reference, false, false)
            return true
        }
    }

    override fun contains(value: T): Boolean {
        val (current, _) = find(value)
        if (current.value != value) return false
        if (current.nextNode.isMarked) {
            reportDelete(current)
            return false
        }
        reportInsert(current)
        return true
    }

    override fun isEmpty(): Boolean {
        return head.nextNode.reference == null
    }

    class Node<T: Comparable<T>>(val value: T?, next: Node<T>?) {
        var nextNode: AtomicMarkableReference<Node<T>?> = AtomicMarkableReference(next, false)
    }

    data class Window<T: Comparable<T>>(val pred: Node<T>, val next: Node<T>?)

    private fun find(value: T): Window<T> {
        while (true) {
            var pred = head
            var current = pred.nextNode.reference
            while (true) {
                if (current == null) return Window(pred, current)
                if (current.nextNode.isMarked) {
                    reportDelete(current)
                    if (!pred.nextNode.compareAndSet(current, current.nextNode.reference, false, false)) break
                } else {
                    if (current.value!! >= value) return Window(pred, current)
                    pred = current
                }
                current = current.nextNode.reference
            }
        }
    }

    override fun iterator(): Iterator<T> =
        takeSnapshot().iterator()

    private fun reportDelete(victim: Node<T>) {
        val sc = psc.get()
        if (sc.isActive)
            sc.addReport(victim, ReportType.DELETED)
    }

    private fun reportInsert(newNode: Node<T>) {
        val sc = psc.get()
        if (sc.isActive)
            if (!newNode.nextNode.isMarked)
                sc.addReport(newNode, ReportType.INSERTED)
    }

    private fun takeSnapshot() : List<T> {
        val sc = acquireSnapCollector()
        collectSnapshot(sc)
        return reconstructUsingReports(sc)
    }

    private fun acquireSnapCollector(): SnapCollector<T> {
        val sc = psc.get()
        if (sc.isActive) return sc
        val newSc = SnapCollector<T>()
        psc.compareAndSet(sc, newSc)
        return psc.get()
    }

    private fun collectSnapshot(sc: SnapCollector<T>) {
        var current: Node<T>? = head
        while (sc.isActive) {
            if (!current!!.nextNode.isMarked)
                current = sc.addNode(current)
            if (current!!.nextNode.reference == null) {
                sc.blockFurtherNodes()
                sc.deactivate()
            }
            current = current.nextNode.reference
        }
        sc.blockFurtherReports()
    }

    private fun reconstructUsingReports(sc: SnapCollector<T>) : ArrayList<T> {
        val snapshot = arrayListOf<Node<T>>()
        val inserted = sc.readReports(ReportType.INSERTED)
        val deleted = sc.readReports(ReportType.DELETED)
        val nodes = sc.readNodes()
        snapshot.addAll(inserted)
        snapshot.addAll(nodes)
        snapshot.removeAll(deleted)
        val result = arrayListOf<T>()
        for (node in snapshot) {
            if (node.value != null)
                result.add(node.value)
        }
        return result
    }

    private class SnapCollector<T: Comparable<T>> {
        private val NUM_THREADS = 256
        private val reports = arrayOfNulls<ReportNode<T>>(NUM_THREADS)
        private val SCHead = SCNode<T>(Node(null, null))
        private val SCTail = AtomicReference(SCHead)
        init {
            for (i in 0 until NUM_THREADS) {
                reports[i] = ReportNode()
            }
        }
        @Volatile var isActive = true

        fun deactivate() {
            isActive = false
        }

        fun addReport(victim: Node<T>, action: ReportType) {
            val newReport = ReportNode(victim, action)
            val id = Thread.currentThread().id.toInt()
            var tail = reports[id] ?: return
            while (tail.next.get() != null) {
                tail = tail.next.get()
            }
            if (tail.type == ReportType.DUMMY) return
            tail.next.compareAndSet(null, newReport)
        }

        fun blockFurtherReports() {
            for (i in 0 until NUM_THREADS) {
                val node = reports[i] ?: continue
                val newNode = ReportNode<T>(null, ReportType.DUMMY)
                val next = node.next.get()
                node.next.compareAndSet(next, newNode)
            }
        }

        fun addNode(node: Node<T>) : Node<T>? {
            val last = SCTail.get()
            if (last.node == null || (last.node.value != null && node.value != null && last.node.value >= node.value)) {
                return last.node
            }

            if (last.next.get() != null) {
                if (last === SCTail.get())
                    SCTail.compareAndSet(last, last.next.get())
                return SCTail.get().node
            }

            val newNode = SCNode(node)
            return if (last.next.compareAndSet(null, newNode)) {
                SCTail.compareAndSet(last, newNode)
                node
            } else {
                SCTail.get().node
            }
        }

        fun blockFurtherNodes() {
            SCTail.set(SCNode(null))
        }

        fun readNodes() : ArrayList<Node<T>> {
            val result = arrayListOf<Node<T>>()
            var current = SCHead
            while (true) {
                current = current.next.get()
                if (current == null) break
                if (current.node == null) break
                result.add(current.node!!)
            }
            return result
        }

        fun readReports(reportType: ReportType) : ArrayList<Node<T>> {
            val result = arrayListOf<Node<T>>()
            for (report in reports) {
                var current = report
                while (current != null) {
                    val node = current.value
                    if (node != null && current.type == reportType) result.add(node)
                    current = current.next.get()
                }
            }
            return result
        }

        private class ReportNode<T: Comparable<T>>(val value: Node<T>? = null, val type: ReportType? = null) {
            var next = AtomicReference<ReportNode<T>>(null)
        }

        private class SCNode<T: Comparable<T>>(val node: Node<T>? = null) {
            var next = AtomicReference<SCNode<T>>(null)
        }
    }

}