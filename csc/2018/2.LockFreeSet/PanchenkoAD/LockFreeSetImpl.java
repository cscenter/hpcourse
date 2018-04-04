package com.company;

import java.util.concurrent.atomic.AtomicMarkableReference;

public class LockFreeSetImpl<T extends Comparable<T>> implements LockFreeSet<T> {

    @Override
    public boolean contains(T value) {
        NodesPair<T> nodes = search(value);
        if (nodes.right == null || nodes.right.value != value) {
            return false;
        }
        return true;
    }

    @Override
    public boolean add(T value) {
        while (true) {
            NodesPair<T> position = search(value);

            if (position.right != null && position.right.value == value) {
                return false;
            }
            if (position.left.nextRef.compareAndSet(position.right, new Node<T>(value, position.right), false, false)) {
                return true;
            } else {
                if (position.left.nextRef.compareAndSet(position.right, position.right, true, false)) {
                    return true;
                }
            }
        }
    }

    @Override
    public boolean remove(T value) {
        while (true) {
            NodesPair<T> position = search(value);
            if (position.right == null) {
                return false;
            }
            if (position.right.value == value) {
                if (position.left.nextRef.compareAndSet(position.right, position.right, false, true)) {
                    return true;
                }
                continue;
            }
            return false;
        }
    }

    @Override
    public boolean isEmpty() {
        return head.nextRef.getReference() == null;
    }

    private class Node<T> {
        final T value;
        final AtomicMarkableReference<Node> nextRef;
        Node(T value, Node<T> next) {
            this.value = value;
            nextRef = new AtomicMarkableReference<>(next, false);
        }
    }

    private final Node<T> head = new Node<>(null, null);

    private class NodesPair<T extends Comparable<T>> {
        final Node<T> left;
        final Node<T> right;
        NodesPair(Node<T> left, Node<T> right) {
            this.left = left;
            this.right = right;
        }
    }

    private NodesPair<T> search(T value) {
        while (true) {
            Node<T> prev = head;
            Node<T> cur = prev.nextRef.getReference();
            while (cur != null) {
                Node<T> result = cur.nextRef.getReference();
                if (cur.nextRef.isMarked()) {
                    if (!prev.nextRef.compareAndSet(cur, result, false, false)) {
                        continue;
                    }
                } else {
                    if (cur.value.compareTo(value) >= 0) {
                        return new NodesPair<>(prev, cur);
                    }
                    prev = cur;
                }
                cur = result;
            }
            return new NodesPair<>(prev, null);
        }
    }
}
