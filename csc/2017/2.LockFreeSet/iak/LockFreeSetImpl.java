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

            if ((next != null) && (next.value.compareTo(value) == 0))
                return false;
            else {
                // pred.next.mark might be read and passed to CAS.
                // However, the mark could change since check and thus
                // this option behaves no better then just CASing preassumedly not marked pred.next
                if (pred.next.compareAndSet(next, new_node, false, false))
                    return true;
                // else retry
            }
        }
    }

    @Override
    public boolean remove(T value)
    {
        while (true) {
            Node pred = findPredecessor(value);
            Node current = pred.next.getReference();

            if ((current == null) || (current.value.compareTo(value) != 0))
                return false;
            else {
                if (current.next.attemptMark(current.next.getReference(), true)) {
                    // try to actually delete this element
                    pred.next.compareAndSet(current, current.next.getReference(), false, false);
                    return true;
                }
                // else retry
            }
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
        return (current != null) && (current.value.compareTo(value) == 0);
    }

    @Override
    public boolean isEmpty()
    {
        return head.next == null;
    }

    private Node findPredecessor(T value)
    {
        Node current = head;
        while ((current.next.getReference() != null) && (current.next.getReference().value.compareTo(value) < 0)) {
            current = current.next.getReference();
        }
        return current;
    }

    private Node findPredecessorWithDeletion(T value)
    {
        Node pred;
        Node current = head;
        while ((current.next.getReference() != null) && (current.next.getReference().value.compareTo(value) < 0)) {
            pred = current;
            current = current.next.getReference();
            if (current.next.isMarked()) {
                // Assume current.next.isMarked() will never change true -> false
                if (pred.next.compareAndSet(current, current.next.getReference(), false, false))
                    current = current.next.getReference();
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
            next = new AtomicMarkableReference<>(null, false);
        }

        Node(T value)
        {
            this.value = value;
            next = new AtomicMarkableReference<>(null, false);
        }

        final T value;
        AtomicMarkableReference<Node> next;
    }

    private final Node head;
}
