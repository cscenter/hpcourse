package build;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicMarkableReference;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public final class LockFreeSetImpl<T extends Comparable<T>> implements LockFreeSet<T>, Iterable<T> {

    private final Node<T> tail = new Node<>();
    private final Node<T> head = new Node<>(null, tail);

    private final AtomicReference<SnapshotCollector<T>> snapshotCollectorRef;

    public LockFreeSetImpl() {
        SnapshotCollector<T> snapshotCollector = new SnapshotCollector<>();
        snapshotCollector.deactivate();
        snapshotCollectorRef = new AtomicReference<>(snapshotCollector);
    }

    @Override
    public boolean add(T value) {
        while (true) {
            NodePair<T> place = findPlace(value);
            Node<T> prev = place.prev;
            Node<T> curr = place.curr;

            // if already exists
            if (Objects.equals(curr.value, value)) {
                reportInsert(curr);
                return false;
            }

            // create new node and try to insert
            Node<T> newNode = new Node<>(value, curr);
            if (prev.next.compareAndSet(curr, newNode, false, false)) {
                reportInsert(newNode);
                return true;
            }
        }
    }

    @Override
    public boolean remove(T value) {
        while (true) {
            NodePair<T> place = findPlace(value);
            Node<T> prev = place.prev;
            Node<T> curr = place.curr;

            // if not presented in the set
            if (!Objects.equals(curr.value, value)) {
                return false;
            }

            Node<T> next = curr.next.getReference();
            // try to delete current logically
            if (!curr.next.compareAndSet(next, next, false, true)) {
                continue;
            }
            reportDelete(curr);
            // delete current physically
            prev.next.compareAndSet(curr, next, false, false);
            return true;
        }
    }

    @Override
    public boolean contains(T value) {
        Node<T> curr = head.next.getReference();
        while (curr != tail) {
            boolean[] marked = {false};
            Node<T> next = curr.next.get(marked);
            if (curr.value.compareTo(value) >= 0) {
                if (Objects.equals(curr.value, value) && !marked[0]) {
                    reportInsert(curr);
                    return true;
                }
                return false;
            }
            curr = next;
        }
        return false;
    }

    @Override
    public boolean isEmpty() {
        Node<T> curr = head.next.getReference();
        while (curr != tail && curr.next.isMarked()) {
            curr = curr.next.getReference();
        }
        return curr == tail;
    }

    @Override
    public Iterator<T> iterator() {
        return takeSnapshot().iterator();
    }

    private void reportDelete(Node<T> victim) {
        if (snapshotCollectorRef.get().isActive()) {
            snapshotCollectorRef.get().addReport(new Report<>(victim, ReportType.DELETE));
        }
    }

    private void reportInsert(Node<T> newNode) {
        if (snapshotCollectorRef.get().isActive() && !newNode.next.isMarked()) {
            snapshotCollectorRef.get().addReport(new Report<>(newNode, ReportType.INSERT));
        }
    }

    private NodePair<T> findPlace(T value) {
        while (true) {
            Node<T> prev = head;
            Node<T> curr = prev.next.getReference();
            while (true) {
                if (curr == tail) {
                    return new NodePair<>(prev, curr);
                }
                boolean[] marked = {false};
                Node<T> next = curr.next.get(marked);
                // if current is logically deleted
                if (marked[0]) {
                    // try to delete it physically
                    if (!prev.next.compareAndSet(curr, next, false, false)) {
                        break;
                    }
                    reportDelete(curr);
                    curr = next;
                } else {
                    if (curr.value.compareTo(value) >= 0) {
                        return new NodePair<>(prev, curr);
                    }
                    prev = curr;
                    curr = next;
                }
            }
        }
    }

    private List<T> takeSnapshot() {
        SnapshotCollector<T> snapshotCollector = acquireSnapshotCollector();
        collectSnapshot(snapshotCollector);
        return reconstructUsingReports(snapshotCollector);
    }

    private SnapshotCollector<T> acquireSnapshotCollector() {
        SnapshotCollector<T> snapshotCollector = snapshotCollectorRef.get();
        if (snapshotCollector.isActive()) {
            return snapshotCollector;
        }
        SnapshotCollector<T> newSnapshotCollector = new SnapshotCollector<>();
        snapshotCollectorRef.compareAndSet(snapshotCollector, newSnapshotCollector);
        return snapshotCollectorRef.get();
    }

    private void collectSnapshot(SnapshotCollector<T> snapshotCollector) {
        boolean[] marked = {false};
        Node<T> curr = head;
        while (snapshotCollector.isActive()) {
            Node<T> next = curr.next.get(marked);
            if (curr.value != null && !marked[0]) {
                snapshotCollector.addNode(curr);
            }
            if (next == null) {
                snapshotCollector.blockFurtherNodes();
                snapshotCollector.deactivate();
            }
            curr = next;
        }
        snapshotCollector.blockFurtherReports();
    }

    private List<Node<T>> convertToNodes(List<Report<T>> reports) {
        return reports.stream()
                .map(Report::getNode)
                .collect(Collectors.toList());
    }

    private List<T> reconstructUsingReports(SnapshotCollector<T> snapshotCollector) {
        List<Node<T>> nodes = snapshotCollector.readNodes();
        List<Report<T>> insertReports = snapshotCollector.readReports(ReportType.INSERT);
        List<Report<T>> deleteReports = snapshotCollector.readReports(ReportType.DELETE);

        nodes.addAll(convertToNodes(insertReports));
        nodes.removeAll(convertToNodes(deleteReports));
        return nodes.stream().map(n -> n.value).collect(Collectors.toList());
    }

    private class SnapshotCollector<T extends Comparable<T>> {
        private static final int MAX_THREADS = 256;

        private volatile boolean active = true;

        private final SNode<Report<T>>[] threadReports = new SNode[MAX_THREADS];
        private final SNode<Node<T>> head = new SNode<>(new Node<>());
        private final AtomicReference<SNode<Node<T>>> tail = new AtomicReference<>(head);

        SnapshotCollector() {
            for (int i = 0; i < MAX_THREADS; i++) {
                threadReports[i] = new SNode<>(null);
            }
        }

        Node<T> addNode(Node<T> node) {
            assert node != null && node.value != null;

            SNode<Node<T>> tailSnapNode = tail.get();
            Node<T> tailNode = tailSnapNode.value;

            // tailNode == null means that blockFurtherNodes was called
            if (tailNode == null || tailNode.value != null && tailNode.value.compareTo(node.value) >= 0) {
                return tailNode;
            }

            SNode<Node<T>> newSnapNode = new SNode<>(node);
            while (true) {
                SNode<Node<T>> nextSnapNode = tailSnapNode.next.get();
                if (nextSnapNode == null) {
                    if (tailSnapNode.next.compareAndSet(null, newSnapNode)) {
                        tail.compareAndSet(tailSnapNode, newSnapNode);
                        return node;
                    }
                }
                if (tail.compareAndSet(tailSnapNode, nextSnapNode)) {
                    return nextSnapNode.value;
                }
            }
        }

        void addReport(Report<T> report) {
            assert report != null;

            int threadId = (int) Thread.currentThread().getId();
            SNode<Report<T>> headSnapNode = threadReports[threadId];
            SNode<Report<T>> nextSnapNode = headSnapNode.next.get();
            // if blockFurtherReports was called
            if (nextSnapNode!= null && nextSnapNode.value == null) {
                return;
            }
            // try to add new node between head and next
            SNode<Report<T>> newSnapNode = new SNode<>(report, nextSnapNode);
            headSnapNode.next.compareAndSet(nextSnapNode, newSnapNode);
        }

        boolean isActive() {
            return active;
        }

        void deactivate() {
            active = false;
        }

        void blockFurtherNodes() {
            tail.set(new SNode<>(null));
        }

        void blockFurtherReports() {
            for (SNode<Report<T>> headSnapNode: threadReports) {
                SNode<Report<T>> nextSnapNode = headSnapNode.next.get();
                SNode<Report<T>> newSnapNode = new SNode<>(null, nextSnapNode);
                headSnapNode.next.compareAndSet(nextSnapNode, newSnapNode);
            }
        }

        List<Node<T>> readNodes() {
            List<Node<T>> result = new LinkedList<>();
            SNode<Node<T>> curr = head.next.get();
            while (curr != null) {
                result.add(curr.value);
                curr = curr.next.get();
            }
            return result;
        }

        List<Report<T>> readReports(ReportType reportType) {
            List<Report<T>> result = new LinkedList<>();
            for (SNode<Report<T>> headSnapNode: threadReports) {
                SNode<Report<T>> curr = headSnapNode.next.get();
                while (curr != null && curr.value != null) {
                    if (curr.value.getReportType() == reportType) {
                        result.add(curr.value);
                    }
                    curr = curr.next.get();
                }
            }
            return result;
        }

        private class SNode<V> {
            final V value;
            final AtomicReference<SNode<V>> next;

            SNode(V value) {
                this.value = value;
                this.next = new AtomicReference<>(null);
            }

            SNode(V value, SNode<V> next) {
                this.value = value;
                this.next = new AtomicReference<>(next);
            }
        }
    }

    private class Node<E> {
        final E value;
        final AtomicMarkableReference<Node<E>> next;

        Node(E value, Node<E> nextNode) {
            this.value = value;
            this.next = new AtomicMarkableReference<>(nextNode, false);
        }

        Node() {
            value = null;
            next = new AtomicMarkableReference<>(null, false);
        }
    }

    private class NodePair<E> {
        final Node<E> prev;
        final Node<E> curr;

        NodePair(Node<E> prev, Node<E> curr) {
            this.prev = prev;
            this.curr = curr;
        }
    }

    private enum ReportType {
        INSERT, DELETE
    }

    private class Report<T> {
        private final Node<T> node;
        private final ReportType reportType;

        public Report(Node<T> node, ReportType reportType) {
            this.node = node;
            this.reportType = reportType;
        }

        public Node<T> getNode() {
            return node;
        }

        public ReportType getReportType() {
            return reportType;
        }
    }
}
