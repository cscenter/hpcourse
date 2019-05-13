package da;

import java.util.*;
import java.util.concurrent.atomic.AtomicMarkableReference;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.stream.Collectors.*;

class LockFreeSetImpl<T extends Comparable<T>> implements LockFreeSet<T> {

    final boolean INSERTED = false;
    final boolean DELETED = true;

    private Node head;
    private AtomicReference<SnapCollector> PSC;

    private class Node {
        T value;
        AtomicMarkableReference<Node> next;

        Node() {
            value = null;
            next = new AtomicMarkableReference<Node>(null, false);
        }

        Node(T item) {
            value = item;
            next = new AtomicMarkableReference<Node>(null, false);
        }
    }

    private class Window {
        Node pred, curr;

        Window(Node predNode, Node currNode) {
            pred = predNode;
            curr = currNode;
        }
    }

    public Window find(Node head, T value) {
        Node pred, curr, next = null;
        boolean[] marked = { false };
        boolean snip;
        retry: while (true) {
            pred = head;
            curr = pred.next.getReference();
            while (curr != null) {
                next = curr.next.get(marked);
                while (marked[0]) {
                    snip = pred.next.compareAndSet(curr, next, false, false);
                    if (!snip) {
                        continue retry;
                    }
                    curr = next;
                    if (curr == null) {
                        return new Window(pred, curr);
                    }
                    next = curr.next.get(marked);
                }
                if (curr.value.compareTo(value) >= 0) {
                    return new Window(pred, curr);
                }
                pred = curr;
                curr = next;
            }
            return new Window(pred, curr);
        }
    }

    private class SnapCollector {
        boolean isActive, blockFurtherPointers, blockFurtherReports;
        Set<Node> insertReports, deleteReports, pointers;

        SnapCollector() {
            isActive = true;
            blockFurtherPointers = false;
            blockFurtherReports = false;
            insertReports = new HashSet<Node>();
            deleteReports = new HashSet<Node>();
            pointers = new HashSet<Node>();
        }

        boolean isActive() {
            return isActive;
        }

        void deactivate() {
            isActive = false;
        }

        void report(Node n, boolean action) {
            if (!blockFurtherReports) {
                if (action == INSERTED) {
                    insertReports.add(n);
                } else if (action == DELETED) {
                    deleteReports.add(n);
                }
            }
        }

        void addNode(Node node) {
            if (!blockFurtherPointers) {
                pointers.add(node);
            }
        }

        void blockFurtherPointers() {
            blockFurtherPointers = true;
        }

        void blockFurtherReports() {
            blockFurtherReports = true;
        }

        void readReports() {
            return;
        }

        void readPointers() {
            return;
        }
    }

    public LockFreeSetImpl() {
        head = new Node();
        PSC = new AtomicReference<SnapCollector>(new SnapCollector());
        PSC.get().deactivate();
    }

    @Override
    public boolean add(T value) {
        while (true) {
            Window w = find(head, value);
            Node pred = w.pred;
            Node curr = w.curr;
            if (curr != null && curr.value.compareTo(value) == 0) {
                reportInsert(curr);
                return false;
            }
            // assert curr.value.compareTo(value) > 0;
            Node node = new Node(value);
            node.next = new AtomicMarkableReference<Node>(curr, false);
            if (pred.next.compareAndSet(curr, node, false, false)) {
                reportInsert(node);
                return true;
            }
        }
    }

    @Override
    public boolean remove(T value) {

        boolean snip;
        while (true) {
            Window w = find(head, value);
            Node pred = w.pred;
            Node curr = w.curr;
            if (curr == null || curr.value.compareTo(value) != 0) {
                return false;
            }
            Node next = curr.next.getReference();
            // snip = curr.next.attemptMark(next, true);
            snip = curr.next.compareAndSet(next, next, false, true);
            if (!snip) {
                continue;
            }
            reportDelete(curr);
            pred.next.compareAndSet(curr, next, false, false);
            return true;
        }
    }

    @Override
    public boolean contains(T value) {
        boolean[] marked = { false };
        Node curr = head.next.getReference();
        while (curr != null && curr.value.compareTo(value) < 0) {
            curr = curr.next.get(marked);
        }
        if (curr != null && curr.value.compareTo(value) == 0) {
            if (curr.next.isMarked()) {
                reportDelete(curr);
                return false;
            } else {
                reportInsert(curr);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isEmpty() {
        return head.next.getReference() == null;
        // return iterator().hasNext();
        // Node curr = head.next.getReference();
        // while (curr != null) {
        // if (curr.next.isMarked()) {
        // curr = curr.next.getReference();
        // }
        // else {
        // return false;
        // }
        // }
        // return true;
    }

    @Override
    public Iterator<T> iterator() {
        return takeSnapshot().iterator();
    }

    public void reportDelete(Node victim) {
        SnapCollector SC = PSC.get();
        if (SC.isActive()) {
            SC.report(victim, DELETED);
        }
    }

    public void reportInsert(Node newNode) {
        SnapCollector SC = PSC.get();
        if (SC.isActive()) {
            Node next = newNode.next.getReference();
            if (newNode.next.compareAndSet(next, next, false, false)) {
                SC.report(newNode, INSERTED);
            }
        }
    }

    public List<T> takeSnapshot() {
        SnapCollector SC = acquireSnapCollector();
        collectSnapshot(head, SC);
        return reconstructUsingReports(SC);
    }

    public SnapCollector acquireSnapCollector() {
        SnapCollector SC = PSC.get();
        if (SC.isActive()) {
            return SC;
        }
        SnapCollector newSC = new SnapCollector();
        PSC.compareAndSet(SC, newSC);
        newSC = PSC.get();
        return newSC;
    }

    public void collectSnapshot(Node head_, SnapCollector SC) {
        boolean[] marked = { false };
        Node curr = head_;
        while (SC.isActive()) {
            Node next = curr.next.get(marked);
            if (!marked[0] && curr.value != null) {
                SC.addNode(curr);
            }
            if (next == null) {
                SC.blockFurtherPointers();
                SC.deactivate();
            }
            curr = next;
        }
        SC.blockFurtherReports();
    }

    public List<T> reconstructUsingReports(SnapCollector SC) {
        Set<Node> snapshot = new HashSet<Node>();
        snapshot.addAll(SC.insertReports);
        snapshot.addAll(SC.pointers);
        snapshot.removeAll(SC.deleteReports);
        List<T> iterable = snapshot.stream().map((x) -> x.value).sorted().collect(toList());
        return iterable;
    }
}