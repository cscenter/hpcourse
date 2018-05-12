package com.example.lockfreeset;

import java.util.concurrent.atomic.AtomicMarkableReference;

public class LockFreeSetImpl<T extends Comparable<T>> implements LockFreeSet<T> {

    private class Node<T extends Comparable<T>> {
        final T value;
        final AtomicMarkableReference<Node<T>> next;

        Node() {
            this.value = null;
            this.next = new AtomicMarkableReference<>(null, false);
        }

        Node(T value) {
            this.value = value;
            this.next = new AtomicMarkableReference<>(null, false);
        }

        Node(T value, Node<T> next) {
            this.value = value;
            this.next = new AtomicMarkableReference<>(next, false);
        }
    }

    private class PairNodes<T extends Comparable<T>> {
        final Node<T> L;
        final Node<T> R;

        PairNodes(Node<T> left) {
            this.L = left;
            this.R = null;
        }

        PairNodes(Node<T> left, Node<T> right) {
            this.L = left;
            this.R = right;
        }
    }

    private PairNodes<T> search(T value) {
        retry:
        do {
            Node<T> previous = head;
            Node<T> current = previous.next.getReference();
            while (current != null) {
                Node<T> res = current.next.getReference();

                if (!previous.next.isMarked()) {
                    if (current.value.compareTo(value) >= 0) {
                        return new PairNodes<>(previous, current);
                    }
                    previous = current;
                } else {
                    if (!previous.next.compareAndSet(current, res, true, false)) {
                        continue retry;
                    }
                }
                current = res;
            }

            return new PairNodes<>(previous);

        } while (true);
    }

    private final Node<T> head = new Node<>();

    @Override
    public boolean add(T value) {
        do {
            PairNodes<T> pair = search(value);
            if (pair.R != null && pair.R.value.equals(value)) {
                return false;
            }
            if (pair.L.next.compareAndSet(pair.L.next.getReference(),
                    new Node<>(value, pair.R), false, false)) {
                return true;
            }
        } while (true);
    }

    @Override
    public boolean remove(T value) {
        do {
            PairNodes<T> pair = search(value);
            if (pair.L.next.getReference() == null || !pair.R.value.equals(value)) {
                return false;
            }
            if (!pair.L.next.isMarked()) {
                if (pair.L.next.compareAndSet(pair.R,
                        pair.R, false, true)) {
                    return true;
                }
            }
        } while (true);
    }

    @Override
    public boolean contains(T value) {
        Node<T> current = head.next.getReference();
        while(current != null && !current.value.equals(value)) {
            current = current.next.getReference();
        }
        return current != null && !current.next.isMarked() && current.value.compareTo(value) == 0;
    }

    @Override
    public boolean isEmpty() {
        return head.next.getReference() == null;
    }
}