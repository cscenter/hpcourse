package ru.compscicenter.mlogachev;

import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicMarkableReference;

public class LockFreeSetImpl<T extends Comparable<T>> implements LockFreeSet<T> {

    private Node head;
    private AtomicInteger created;
    private AtomicInteger marked_for_deletion;

    public LockFreeSetImpl() {
        head = new Node(new Node(null, null), null);
        created = new AtomicInteger(0);
        marked_for_deletion = new AtomicInteger(0);
    }

    @Override
    public boolean add(T value) {
        if (value == null) return false;

        while (true) {
            NodePair pair = find(head, value);
            Node prev = pair.left;
            Node curr = pair.right;
            if (!checkDummy(curr) && curr.val.compareTo(value) == 0) {
                return false;
            } else {
                Node node = new Node(curr, value);
                if (prev.next.compareAndSet(curr, node, false, false)) {
                    created.addAndGet(1);
                    return true;
                }
            }
        }
    }

    @Override
    public boolean remove(T value) {
        boolean success;
        while (true) {
            NodePair pair = find(head, value);
            Node pred = pair.left;
            Node curr = pair.right;

            if (checkDummy(curr) || curr.val.compareTo(value) != 0) {
                return false;
            } else {
                Node next = curr.next.getReference();

                success = curr.next.attemptMark(next, true);
                if (!success) continue;

                pred.next.compareAndSet(curr, next, false, false);
                marked_for_deletion.addAndGet(1);

                return true;
            }
        }
    }

    @Override
    public boolean contains(T value) {
        Node current = head.next.getReference();

        while (!checkDummy(current) && current.val.compareTo(value) < 0) {
            current = current.next.getReference();
        }

        if (checkDummy(current)) {
            return false;
        }

        boolean[] marked = {false};
        current.next.get(marked);
        return (current.val.compareTo(value) == 0 && !marked[0]);
    }

    @Override
    public boolean isEmpty() {
        // first check that head.next == tail
        if (checkDummy(head.next.getReference())) return true;


        // then go and cleanup all marked nodes
        Node curr = head.next.getReference();
        Node next = curr.next.getReference();

        boolean[] marked = {curr.next.isMarked()};

        while (!checkDummy(next) && marked[0]) {
            boolean success = head.next.compareAndSet(curr, next, false, false);
            if (!success) {
                // something changed, so either insert or delete was called
                return false;
            }
            curr = next;
            next = curr.next.getReference();
        }

        // if didn't get to tail, some unmarked node must exist
        if (!checkDummy(next)) return false;

        // got to tail
        // so if head.next != tail, then some additions happened, while we were away
        return checkDummy(head.next.getReference());

    }

    @Override
    public Iterator<T> iterator() {
        return null;
    }

    private NodePair find(Node start, T value) {
        boolean[] marked = {false};
        retry: while (true) {

            Node prev = start;
            Node curr = prev.next.getReference();

            while (true) {
                if (checkDummy(curr)) {
                    return new NodePair(prev, curr);
                }

                Node next = curr.next.get(marked);

                while (marked[0]) {
                    boolean cas_success = prev.next.compareAndSet(curr, next, false, false);
                    if (!cas_success) continue retry;

                    curr = next;
                    next = curr.next.get(marked);
                }

                if (curr.val.compareTo(value) >= 0) {
                    return new NodePair(prev, curr);
                }

                prev = curr;
                curr = next;
            }
        }
    }

    private boolean checkDummy(Node n) {
        return n.val == null;
    }

    private final class NodePair {
        Node left;
        Node right;

        NodePair(final Node left, final Node right) {
            this.left = left;
            this.right = right;
        }
    }

    class Node {
        AtomicMarkableReference<Node> next;
        T val;

        Node() {
            this.next = null;
            this.val = null;
        }

        Node(final Node next, final T val) {
            this.next = new AtomicMarkableReference<>(next, false);
            this.val = val;
        }

        @Override
        public String toString() {
            return "Node{" +
                    "next=" + next +
                    ", val=" + val +
                    ", dummy=" + (val == null) +
                    '}';
        }
    }

}

