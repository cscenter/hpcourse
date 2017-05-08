package iak;


import java.util.concurrent.atomic.AtomicMarkableReference;

/**
 * Generic lock-free concurrent set based on a singly-linked list
 * @param <T>
 */
public class LockFreeSetImpl<T extends Comparable<T>> implements LockFreeSet<T> {

    public LockFreeSetImpl()
    {
        head = new Node();
    }

    @Override
    public boolean add(T value)
    {
        // Allocate new node only once
        Node new_node = new Node(value);
        // Retry after each failed attempt
        while (true) {
            Node pred = findPredecessor(value);
            Node next = pred.next.getReference();

            if (next != null && next.value.compareTo(value) == 0)
                return false;

            new_node.next.set(next, false);
            boolean marked = pred.next.isMarked();
            if (pred.next.compareAndSet(next, new_node, marked, marked))
                return true;
            // else retry
        }
    }

    @Override
    public boolean remove(T value)
    {
        while (true) {
            Node pred = findPredecessor(value);
            Node current = pred.next.getReference();

            if (current == null || current.value.compareTo(value) != 0)
                return false;

            if (current.next.attemptMark(current.next.getReference(), true)) {
                // try to actually delete this element
                boolean marked = pred.next.isMarked();
                pred.next.compareAndSet(current, current.next.getReference(), marked, marked);
                return true;
            }
            // else retry
        }
    }

    /**
     * This method deletes every logically deleted elements on its path
     * @param value значение ключа
     * @return
     */
    @Override
    public boolean contains(T value)
    {
        Node current = findPredecessorWithDeletion(value).next.getReference();
        return current != null && current.value.compareTo(value) == 0;
    }

    @Override
    public boolean isEmpty()
    {
        return head.next == null;
    }

    private Node findPredecessor(T value)
    {
        Node current = head;
        Node next = current.next.getReference();
        while (next != null && (next.next.isMarked() || next.value.compareTo(value) < 0)) {
            current = next;
            next = current.next.getReference();
        }
        return current;
    }

    private Node findPredecessorWithDeletion(T value)
    {
        Node pred = head;
        Node current = head;
        Node next = current.next.getReference();
        while (next != null && next.value.compareTo(value) < 0) {
            current = next;
            next = current.next.getReference();

            if (current.next.isMarked()) {
                // Assume current.next.isMarked() will never change true -> false
                if (!pred.next.compareAndSet(current, next, false, false)) {
                    pred = current;
                }
            }
            else {
                pred = current;
            }
        }
        return current;
    }

    /**
     * Reference to the next node is combined with self deletion mark
     * This enables CAS operations on a node
     */
    private class Node {

        Node()
        {
            value = null;
            next = new AtomicMarkableReference<Node>(null, false);
        }

        Node(T value)
        {
            this.value = value;
            next = new AtomicMarkableReference<Node>(null, false);
        }

        final T value;
        AtomicMarkableReference<Node> next;
    }

    private final Node head;
}
