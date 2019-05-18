package da;

import java.util.*;
import java.util.concurrent.atomic.AtomicMarkableReference;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.stream.Collectors.*;

class LockFreeSetImpl<T extends Comparable<T>> implements LockFreeSet<T> {

    final boolean INSERTED = false;
    final boolean DELETED = true;
    final int MAX_THREADS = 1024;

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

        private class NodeNode<Node> {
            Node node;
            boolean report_type;
            AtomicReference<NodeNode> next;

            public NodeNode(Node node_, NodeNode next_) {
                report_type = INSERTED;
                node = node_;
                next = new AtomicReference<NodeNode>(next_);
            }

            public NodeNode(Node node_, NodeNode next_, boolean type) {
                report_type = type;
                node = node_;
                next = new AtomicReference<NodeNode>(next_);
            }
        }
        
        boolean isActive;
        NodeNode<Node>[] reports, pointers;

        SnapCollector() {
            isActive = true;

            reports = new NodeNode[MAX_THREADS];
            for (int i = 0; i < reports.length; i++) {
                reports[i] = new NodeNode(null, null);
            }

            pointers = new NodeNode[MAX_THREADS];
            for (int i = 0; i < pointers.length; i++) {
                pointers[i] = new NodeNode(null, null);
            }

        }

        boolean isActive() {
            return isActive;
        }

        void deactivate() {
            isActive = false;
        }

        boolean report(Node node, boolean action) {

            int id = (int) Thread.currentThread().getId();
            NodeNode<Node> tail = reports[id];
            while (true) {
                NodeNode<Node> next = tail.next.get();
                if (next != null && next.node == null) {
                    return false;
                }
                NodeNode<Node> newNode = new NodeNode(node, next, action);
                if (tail.next.compareAndSet(next, newNode)) {
                    return true;
                }

            }
    }

        boolean addNode(Node node) {

            int id = (int) Thread.currentThread().getId();
            NodeNode<Node> tail = pointers[id];
            while (true) {
                NodeNode<Node> next = tail.next.get();
                if (next != null && next.node == null) {
                    return false;
                }
                NodeNode<Node> newNode = new NodeNode(node, next);
                if (tail.next.compareAndSet(next, newNode)) {
                    return true;
                }

            }

        }

        void blockFurtherPointers() {
            for (int i = 0; i < pointers.length; i++) {
                NodeNode<Node> tail = pointers[i];
                NodeNode<Node> next = tail.next.get();
                if (tail.next.compareAndSet(next, new NodeNode<Node>(null, next))) {
                }
            }
        }

        void blockFurtherReports() {
            for (int i = 0; i < reports.length; i++) {
                NodeNode<Node> tail = reports[i];
                NodeNode<Node> next = tail.next.get();
                tail.next.compareAndSet(next, new NodeNode<Node>(null, next));
            }
        }

        HashSet<Node> readReports() {
            HashSet<Node> result = new HashSet<>();

            for (int i = 0; i < reports.length; i++) {
                NodeNode tail = reports[i];
                NodeNode curr = tail;
                while (curr != null) {
                    Node node = (Node) curr.node;
                    boolean action = curr.report_type;
                    if (node != null) {
                        if (action == INSERTED) {
                            result.add(node);
                        } else {
                            result.remove(node);
                        }
                    }
                    curr = (NodeNode) curr.next.get();
                }
            }
            return result;
        }

        HashSet<Node> readPointers() {
            HashSet<Node> result = new HashSet<>();
            for (int i = 0; i < pointers.length; i++) {
                NodeNode tail = pointers[i];
                NodeNode curr = tail;
                while (curr != null) {
                    Node node = (Node) curr.node;
                    if (node != null) result.add(node);
                    curr = (NodeNode) curr.next.get();
                }
            }
            return result;
        }
    }

    public LockFreeSetImpl() {
        head = new Node();
        PSC = new AtomicReference<SnapCollector>(new SnapCollector());
        PSC.get().deactivate();
    }

    @Override
    public boolean add(T value) {
        checkThreadId();
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
        checkThreadId();
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
        checkThreadId();

        boolean[] marked = { false };
        Node curr = head.next.getReference();
        while (curr != null && curr.value.compareTo(value) < 0) {
            curr = curr.next.get(marked);
        }
        if (curr != null && curr.value.compareTo(value) == 0) {
            if (curr.next.isMarked()) {
            // if (marked[0]) {
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
        checkThreadId();
        return !iterator().hasNext();
        // while (true) {
        //     AtomicMarkableReference<Node> next = head.next;
        //     if (next.getReference() != null && !next.isMarked()) {
        //         return false;
        //     }
        //     else if (next.getReference() == null) {
        //         return true;
        //     }
        //     Node nextnext = next.getReference().next.getReference();
        //     head.next.compareAndSet(next.getReference(), nextnext, false, false);

        // }
    }

    @Override
    public Iterator<T> iterator() {
        checkThreadId();
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

        // boolean[] marked = { false };
        Node curr = head_;
        while (SC.isActive()) {
            // Node next = curr.next.get(marked);
            // if (curr.value != null && !marked[0]) {
            AtomicMarkableReference<Node> next = curr.next;
            if (curr.value != null && !next.isMarked()) {
                SC.addNode(curr);
            }
            if (next.getReference() == null) {
                SC.deactivate();
                SC.blockFurtherPointers();

            }
            curr = next.getReference();
        }
        SC.blockFurtherReports();
    }

    public List<T> reconstructUsingReports(SnapCollector SC) {
        Set<Node> snapshot = new HashSet<Node>();
        snapshot.addAll(SC.readPointers());
        snapshot.addAll(SC.readReports());
        List<T> iterable = snapshot.stream().map((x) -> x.value).sorted().collect(toList());
        return iterable;
    }

    public boolean checkThreadId() {
        if ((int) Thread.currentThread().getId() >= MAX_THREADS) {
            throw new RuntimeException("max threads " + Integer.toString(MAX_THREADS));
        }
        return true;
    }
}
