import java.util.concurrent.atomic.AtomicMarkableReference;

/**
 * Generic lock-free concurrent set based on a singly-linked list
 * @param <T>
 */
public class LockFreeSetImpl<T extends Comparable<T>> implements LockFreeSet<T> {

    LockFreeSetImpl()
    {
        head = new Node();
    }

    @Override
    public boolean add(T value)
    {
        Node pred = findPredecessor(value);
        Node next= pred.next.getReference();

        if ((next != null) && (next.value.compareTo(value) == 0))
            return false;
        else {
            Node new_node = new Node(value);
            if (pred.next.compareAndSet(next, new_node, false, false))
                return true;
            // retry
            return add(value);
        }
    }

    @Override
    public boolean remove(T value)
    {
        Node pred = findPredecessor(value);
        Node next = pred.next.getReference();

        if ((next == null) || (next.value.compareTo(value) != 0))
            return false;
        else {
            if (pred.next.attemptMark(next, true)) {
                // try to actually delete this element
                pred.next.compareAndSet(next, next.next.getReference(), false, false);
                return true;
            }
            return remove(value);
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
            if (pred.next.compareAndSet(current, current.next.getReference(), true, false)) {
                current = current.next.getReference();
            }
        }
        return current;
    }

    /**
     * Reference to the next node is combined with its deletion mark
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

    private Node head;
}
