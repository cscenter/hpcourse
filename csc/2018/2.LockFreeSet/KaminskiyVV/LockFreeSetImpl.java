package com.example.lockfreeset;

import java.util.concurrent.atomic.AtomicMarkableReference;

public class LockFreeSetImpl<T extends Comparable<T>> implements LockFreeSet<T> {

    private class Node<T extends Comparable<T>> {
        T value;
        AtomicMarkableReference<Node<T>> next;

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
        Node<T> L;
        Node<T> R;

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
        do {
            Node<T> previous = head;
            Node<T> current = previous.next.getReference();
            while (current != null) {
                Node<T> res = current.next.getReference();

                if (!current.next.isMarked()) {
                    if (current.value.compareTo(value) >= 0) {
                        return new PairNodes<>(previous, current);
                    }
                    previous = current;
                } else {
                    if (!previous.next.compareAndSet(current, res, false, false)) {
                        continue;
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
            if (pair.R != null && pair.R.value == value) {
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
            if (pair.L.next.getReference() == null || pair.R.value != value) {
                return false;
            }
            if (!pair.R.next.isMarked()) {
                if (pair.R.next.compareAndSet(pair.R.next.getReference(),
                        pair.R.next.getReference(), false, true)) {
                    return true;
                }
            }
        } while (true);
    }

    @Override
    public boolean contains(T value) {
        PairNodes<T> pair = search(value);
        if (pair.R == null || pair.R.value != value) {
            return false;
        }
        return true;
    }

    @Override
    public boolean isEmpty() {
        return head.next.getReference() == null;
    }
}
