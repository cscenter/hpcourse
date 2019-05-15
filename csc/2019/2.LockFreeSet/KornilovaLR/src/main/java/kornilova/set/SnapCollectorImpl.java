package kornilova.set;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Lock-free implementation
 */
public class SnapCollectorImpl<T extends Comparable<T>> implements SnapCollector<T> {
    private static final int THREADS_COUNT = 512;
    private volatile boolean isActive = true;
    private final SCNode<Report<T>>[] myReportsTails; // reports are blocked if value in first node is null
    private final SCNode<Node<T>>[] myNodesTails; // nodes are blocked if value in first node is null

    SnapCollectorImpl() {
        //noinspection unchecked
        myReportsTails = new SCNode[THREADS_COUNT];
        for (int i = 0; i < myReportsTails.length; i++) {
            myReportsTails[i] = new SCNode<>(null, null);
        }
        //noinspection unchecked
        myNodesTails = new SCNode[THREADS_COUNT];
        for (int i = 0; i < myNodesTails.length; i++) {
            myNodesTails[i] = new SCNode<>(null, null);
        }
    }

    @Override
    public boolean isActive() {
        return isActive;
    }

    @Override
    public void deactivate() {
        isActive = false;
    }

    @Override
    public void addNode(Node<T> node) {
        if (node == null) throw new IllegalArgumentException("report is null");

        int id = (int) Thread.currentThread().getId();
        SCNode<Node<T>> nodeTail = myNodesTails[id];
        while (true) {
            SCNode<Node<T>> next = nodeTail.myNext.get();
            if (next != null && next.myValue == null) return; // blockFurtherNodes was called
            SCNode<Node<T>> newNode = new SCNode<>(node, next);
            if (nodeTail.myNext.compareAndSet(next, newNode)) {
                return;
            }
        }
    }

    @Override
    public void blockFurtherNodes() {
        for (SCNode<Node<T>> nodeTail : myNodesTails) {
            AtomicReference<SCNode<Node<T>>> ref = nodeTail.myNext;
            SCNode<Node<T>> next = ref.get();
            ref.compareAndSet(next, new SCNode<>(null, next));
        }
    }

    @Override
    public void blockFurtherReports() {
        for (SCNode<Report<T>> reportTail : myReportsTails) {
            AtomicReference<SCNode<Report<T>>> ref = reportTail.myNext;
            SCNode<Report<T>> next = ref.get();
            ref.compareAndSet(next, new SCNode<>(null, next));
        }
    }

    @Override
    public HashSet<Node<T>> readNodes() {
        HashSet<Node<T>> result = new HashSet<>();
        for (SCNode<Node<T>> nodeTail : myNodesTails) {
            SCNode<Node<T>> curr = nodeTail;
            while (curr != null) {
                Node<T> node = curr.myValue;
                if (node != null) result.add(node);
                curr = curr.myNext.get();
            }
        }
        return result;
    }

    @Override
    public Collection<Report<T>> readReports() {
        List<Report<T>> result = new ArrayList<>();
        for (SCNode<Report<T>> reportTail : myReportsTails) {
            SCNode<Report<T>> curr = reportTail;
            while (curr != null) {
                Report<T> report = curr.myValue;
                if (report != null) result.add(report);
                curr = curr.myNext.get();
            }
        }
        return result;
    }

    @Override
    public void report(Report<T> report) {
        if (report == null) throw new IllegalArgumentException("report is null");
        int id = (int) Thread.currentThread().getId();
        SCNode<Report<T>> reportsTail = myReportsTails[id];
        while (true) {
            SCNode<Report<T>> next = reportsTail.myNext.get();
            if (next != null && next.myValue == null) return; // blockFurtherReports was called
            SCNode<Report<T>> newNode = new SCNode<>(report, next);
            if (reportsTail.myNext.compareAndSet(next, newNode)) {
                return;
            }
        }
    }

    private class SCNode<V> {
        final V myValue;
        final AtomicReference<SCNode<V>> myNext;

        private SCNode(V myValue, SCNode<V> myNext) {
            this.myValue = myValue;
            this.myNext = new AtomicReference<>(myNext);
        }
    }
}
