package com.company;

import java.util.concurrent.atomic.AtomicMarkableReference;

/**
 * Created by Fata1ist95 on 04.05.2017.
 */
public class LockFreeList<T extends Comparable<T>> implements LockFreeSet<T> {
    private final AtomicMarkableReference<Node> head;

    public LockFreeList() {
        Value minValue = new Value(ValueType.MIN);
        Value maxValue = new Value(ValueType.MAX);

        Node tailNode = new Node(new AtomicMarkableReference<>(null, false), maxValue);
        Node headNode = new Node(new AtomicMarkableReference<>(tailNode, false), minValue);
        head = new AtomicMarkableReference<>(headNode, false);
    }

    private PNode find(Value key) {
        retry:
        while (true) {
            Node pred = head.getReference();

            Node curr = pred.getNext().getReference(), succ;

            while (true) {
                succ = curr.getNext().getReference();

                if (pred.getNext().isMarked()) {
                    boolean flag = curr.getNext().isMarked();

                    if (!pred.getNext().compareAndSet(curr, succ, true, flag)) {
                        continue retry;
                    }
                    curr = succ;
                } else {
                    Value currValue = curr.getValue();
                    if (currValue.compareTo(key) != -1) {
                        return new PNode(pred, curr);
                    }

                    pred = curr;
                    curr = succ;
                }
            }
        }
    }

    /**
     * Adds key to the set.
     *
     * Type: lock-free
     *
     * @param value value of the key
     * @return false if key already exists in the set, true if key was successfully added
     */
    private boolean add(Value value) {
        while (true) {
            PNode found = find(value);
            Node pred = found.getFirst(), curr = found.getSecond();

            if (curr.getValue().equals(value)) {
                return false;
            }

            Node item = new Node(new AtomicMarkableReference<>(curr, false), value);
            if (pred.getNext().compareAndSet(curr, item, false, false)) {
                return true;
            }
        }
    }

    /**
     * Remove key from the set.
     *
     * Type: lock-free
     *
     * @param value value of the key
     * @return false if key wasn't found, true if key was found and erased
     */

    private boolean remove(Value value) {
        while (true) {
            PNode found = find(value);
            Node pred = found.getFirst(), curr = found.getSecond();
            if (!curr.getValue().equals(value)) {
                return false;
            }

            Node succ = curr.getNext().getReference();
            if (!pred.getNext().compareAndSet(curr, curr, false, true)) {
                continue;
            }

            pred.getNext().compareAndSet(curr, succ, false, false);
            return true;
        }
    }

    /**
     * Find key in the set.
     *
     * Type: wait-free
     *
     * @param value value of the key
     * @return true if key already exists in the set, otherwise false
     */

    private boolean contains(Value value) {
        if (value == null) {
            return false;
        }

        AtomicMarkableReference<Node> curr = head;
        while (curr.getReference().getValue().compareTo(value) == -1) {
            curr = curr.getReference().getNext();
        }
        return (curr.getReference().getValue().equals(value) && !curr.isMarked());
    }

    /**
     * Check if the set is empty.
     *
     * Type: wait-free
     *
     * @return true if the set is empty, otherwise false
     */
    public boolean isEmpty() {
        Value secondValue = head.getReference().getNext().getReference().getValue();
        return secondValue.getType().equals(ValueType.MAX);
    }

    //internal calls
    public boolean add(T value) {
        return add(new Value(value));
    }

    public boolean remove(T value) {
        return remove(new Value(value));
    }

    public boolean contains(T value) {
        return contains(new Value(value));
    }

    //internal classes
    private enum ValueType {
        MIN, NORMAL, MAX
    }

    private class Value {
        private final ValueType type;
        private final T value;

        Value(ValueType type) {
            this.type = type;
            this.value = null;
        }

        Value(T value) {
            this.type = ValueType.NORMAL;
            this.value = value;
        }

        ValueType getType() {
            return type;
        }

        T getValue() {
            return value;
        }

        boolean equals(Value o) {
            return type.equals(o.getType()) &&
                    (type.equals(ValueType.MIN) || type.equals(ValueType.MAX) || value.equals(o.getValue()));
        }

        int compareTo(Value o) {
            Integer intType = type.ordinal();
            Integer oIntType = o.getType().ordinal();
            if (!intType.equals(oIntType)) {
                return intType.compareTo(oIntType);
            }

            //both types = MIN or MAX
            if (type.equals(ValueType.MIN) || type.equals(ValueType.MAX)) {
                return 0;
            }

            //otherwise type = NORMAL
            return value.compareTo(o.getValue());
        }
    }

    private class Node {
        private final AtomicMarkableReference<Node> next;
        private final Value value;

        Node(AtomicMarkableReference<Node> next, Value value) {
            this.next = next;
            this.value = value;
        }

        AtomicMarkableReference<Node> getNext() {
            return next;
        }

        Value getValue() {
            return value;
        }
    }

    private class PNode {
        private final Node first, second;

        private PNode(Node first, Node second) {
            this.first = first;
            this.second = second;
        }

        Node getFirst() {
            return first;
        }

        Node getSecond() {
            return second;
        }
    }
}
