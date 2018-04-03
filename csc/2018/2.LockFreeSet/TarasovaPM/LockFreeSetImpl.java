package ru.cscenter;

import java.util.concurrent.atomic.AtomicMarkableReference;

public class LockFreeSetImpl<T extends Comparable<T> > implements LockFreeSet<T> {

    private final Node head = new Node();

    private class Node {
        private final T value;
        private final AtomicMarkableReference<Node> next;

        Node(T value, Node next) {
            this.value = value;
            this.next = new AtomicMarkableReference<>(next, false);
        }

        Node() {
            this.value = null;
            this.next = new AtomicMarkableReference<>(null, false);
        }

        public AtomicMarkableReference<Node> getNext() {
            return next;
        }

        public T getValue() {
            return value;
        }
    }

    private class PairedNode {
        private final Node first;
        private final Node second;

        private PairedNode(Node first, Node second) {
            this.first = first;
            this.second = second;
        }

        public Node getFirst() {
            return first;
        }

        public Node getSecond() {
            return second;
        }
    }

    private PairedNode find(T value) {
        while (true) {
            Node prev = head;
            AtomicMarkableReference<Node> currRef = prev.getNext();
            Node curr = currRef.getReference();

            while (true) {
                if (curr == null) {
                    return new PairedNode(prev, curr);
                }

                Node next = curr.getNext().getReference();
                currRef = prev.getNext();

                if (currRef.isMarked()) {
                    if (!currRef.compareAndSet(curr, next, false, false)) {
                        break;
                    }
                    curr = next;
                } else {
                    if (!curr.getValue().equals(value)) {
                        prev = curr;
                        curr = next;
                    }
                    else
                    {
                        return new PairedNode(prev, curr);
                    }
                }
            }
        }
    }

    public boolean add(T value) {
        while (true) {
            PairedNode found = find(value);
            Node prev = found.getFirst();
            Node curr = found.getSecond();

            if (curr != null && curr.getValue().equals(value)) {
                return false;
            }

            if (prev.getNext().compareAndSet(curr, new Node(value, curr), false, false)) {
                return true;
            }
        }
    }

    public boolean remove(T value) {
        while (true) {
            PairedNode found = find(value);
            Node prev = found.getFirst();
            Node curr = found.getSecond();
            if (curr == null || !curr.getValue().equals(value)) {
                return false;
            }

            Node next = curr.getNext().getReference();
            if (!curr.getNext().attemptMark(next, true)) {
                continue;
            }
            prev.getNext().compareAndSet(curr, next, false, false);
            return true;
        }
    }

    public boolean contains(T value) {
        if (value == null) {
            return false;
        }

        AtomicMarkableReference<Node> currRef = head.getNext();
        Node curr = currRef.getReference();
        while (curr != null && !curr.getValue().equals(value)) {
            curr = curr.getNext().getReference();
        }
        boolean res = curr != null && curr.getValue().equals(value) && !curr.getNext().isMarked();
        return res;
    }

    public boolean isEmpty() {
        return head.getNext().getReference() == null;
    }

}
