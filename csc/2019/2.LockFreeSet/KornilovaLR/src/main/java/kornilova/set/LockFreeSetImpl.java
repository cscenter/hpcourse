package kornilova.set;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static kornilova.set.SnapCollector.ReportType.DELETE;
import static kornilova.set.SnapCollector.ReportType.INSERT;

public class LockFreeSetImpl<T extends Comparable<T>> implements LockFreeSet<T> {
    private final Node<T> myHead;
    private final AtomicReference<SnapCollector<T>> myCollector = new AtomicReference<>();

    public LockFreeSetImpl() {
        myHead = new Node<>(null, null);
        myCollector.set(new SnapCollectorImpl<>());
        myCollector.get().deactivate();
    }

    @Override
    public boolean add(T value) {
        if (value == null) throw new IllegalArgumentException("Value cannot be null");

        while (true) {
            Place place = find(value, true);
            Node<T> pred = place.pred;
            Node<T> curr = place.curr;

            if (curr != null && eq(value, curr.myValue)) {
                reportInsert(curr);
                return false;
            }
            Node<T> newNode = new Node<>(value, curr);
            if (pred.myNext.compareAndSet(curr, newNode, false, false)) {
                reportInsert(newNode);
                return true;
            }
        }
    }

    private Place find(T value, boolean shouldReportDeleted) {
        while (true) {
            Place place = findIteration(value, shouldReportDeleted);
            if (place != null) return place;
        }
    }

    private Place findIteration(T value, boolean shouldReportDeleted) {
        Node<T> pred, curr, next;
        pred = myHead;
        curr = pred.myNext.getReference();
        while (curr != null) {
            boolean[] marked = {false};
            next = curr.myNext.get(marked);
            if (marked[0]) {
                if (shouldReportDeleted) reportDelete(curr);
                if (!pred.myNext.compareAndSet(curr, next, false, false)) {
                    return null; // try again
                }
                curr = next;
                continue;
            }
            if (curr.myValue.compareTo(value) >= 0) {
                return new Place(pred, curr);
            }
            pred = curr;
            curr = next;
        }
        return new Place(pred, null);
    }

    @Override
    public boolean remove(T value) {
        while (true) {
            Place place = find(value, false);
            Node<T> curr = place.curr;
            Node<T> pred = place.pred;

            if (curr == null || !eq(value, curr.myValue)) return false;
            if (!tryMark(curr)) {
                continue;
            }
            reportDelete(curr);
            tryRemove(pred, curr);
            return true;
        }
    }

    private boolean tryMark(Node<T> curr) {
        Node<T> next = curr.myNext.getReference();
        return curr.myNext.compareAndSet(next, next, false, true);
    }

    private boolean tryRemove(Node<T> pred, Node<T> curr) {
        return pred.myNext.compareAndSet(curr, curr.myNext.getReference(), false, false);
    }

    private void reportDelete(Node<T> curr) {
        SnapCollector<T> collector = myCollector.get();
        if (!collector.isActive()) return;
        collector.report(new Report<>(DELETE, curr));
    }

    private void reportInsert(Node<T> curr) {
        SnapCollector<T> collector = myCollector.get();
        if (!collector.isActive()) return;
        collector.report(new Report<>(SnapCollector.ReportType.INSERT, curr));
    }

    @Override
    public boolean contains(T value) {
        Node<T> curr, next;
        curr = myHead.myNext.getReference();
        while (curr != null) {
            boolean[] marked = {false};
            next = curr.myNext.get(marked);
            if (curr.myValue.compareTo(value) >= 0) {
                if (!marked[0] && eq(curr.myValue, value)) {
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
        return !iterator().hasNext();
    }

    private Collection<T> takeSnapshot() {
        SnapCollector<T> collector = acquireSnapCollector();
        collectSnapshot(collector);
        return reconstructUsingReports(collector);
    }

    private SnapCollector<T> acquireSnapCollector() {
        SnapCollector<T> collector = myCollector.get();
        if (collector.isActive()) return collector;
        SnapCollector<T> newCollector = new SnapCollectorImpl<>();
        myCollector.compareAndSet(collector, newCollector);
        return myCollector.get();
    }

    private void collectSnapshot(SnapCollector<T> collector) {
        boolean[] marked = {false};
        Node<T> curr = myHead;
        while (collector.isActive()) {
            Node<T> next = curr.myNext.get(marked);
            if (!marked[0] && curr.myValue != null) {
                collector.addNode(curr);
            }
            if (next == null) {
                collector.blockFurtherNodes();
                collector.deactivate();
            }
            curr = next;
        }
        collector.blockFurtherReports();
    }

    /**
     * 71. ReconstructUsingReports(SC)
     * 72.   nodes = SC.ReadPointers()
     * 73.   reports = SC.ReadReports()
     * 74.   a node N belong to the snapshot iff:
     * 75.     ((N has a reference in nodes OR N has an INSERTED report) AND (N does not have a DELETED report)
     */
    private Collection<T> reconstructUsingReports(SnapCollector<T> collector) {
        TreeSet<T> result = new TreeSet<>();
        HashSet<Node<T>> nodes = collector.readNodes();
        Collection<Report<T>> reports = collector.readReports();
        HashSet<Node<T>> deletedNodes = getReports(reports, DELETE);
        HashSet<Node<T>> insertedNodes = getReports(reports, INSERT);
        for (Node<T> node : nodes) {
            if (!deletedNodes.contains(node)) result.add(node.myValue);
        }
        for (Node<T> node : insertedNodes) {
            if (!deletedNodes.contains(node)) result.add(node.myValue);
        }
        return result;
    }

    private HashSet<Node<T>> getReports(Collection<Report<T>> reports, SnapCollector.ReportType type) {
        HashSet<Node<T>> nodes = new HashSet<>();
        for (Report<T> report : reports) {
            try {
                if (report.type == type) nodes.add(report.node);
            } catch (NullPointerException e) {
                System.out.println("report: " + report);
            }
        }
        return nodes;
    }

    @Override
    public Iterator<T> iterator() {
        return new DelegatingIterator<>(takeSnapshot().iterator());
    }

    private boolean eq(T v1, T v2) {
        if (v1 == null && v2 == null) return true;
        if (v1 == null || v2 == null) return false;
        return v1.compareTo(v2) == 0;
    }

    private class Place {
        final Node<T> pred;
        final Node<T> curr;

        private Place(Node<T> pred, Node<T> curr) {
            this.pred = pred;
            this.curr = curr;
        }
    }
}