import java.util.concurrent.atomic.AtomicMarkableReference;

/**
 * Created by anatoliy on 4/14/17.
 */
public class LockFreeSetImpl<T extends Comparable<T>> implements LockFreeSet<T> {
    private Node head;

    LockFreeSetImpl() {
        head = new Node();
    }

    @Override
    public boolean add(T value) {
        Node pred = findPredecessor(value);
        Node next = pred.next.getReference();

        if (next != null && next.value.compareTo(value) == 0) {
            return false;
        } else {
            Node newNode = new Node(value);
            newNode.next = new AtomicMarkableReference<>(next, false);
            if (pred.next.compareAndSet(next, newNode, false, false))
                return true;
            return add(value);
        }
    }

    @Override
    public boolean remove(T value) {
        Node pred = findPredecessor(value);
        Node found = pred.next.getReference();
        Node next = found.next.getReference();
        if (found == null || found.value.compareTo(value) != 0) {
            return false;
        } else {
            if (found.next.attemptMark(next, true)) {
                pred.next.compareAndSet(found, next, false, false);
                return true;
            }
            return remove(value);
        }
    }

    private Node findPredecessor(T value) {
        Node cur = head;
        while ((cur.next.getReference() != null) && (cur.next.getReference().value.compareTo(value) < 0)) {
            cur = cur.next.getReference();
        }
        return cur;
    }

    @Override
    public boolean contains(T value) {
        Node cur = findPredecessor(value).next.getReference();
        return (cur != null) && (cur.value.compareTo(value) == 0);
    }

    @Override
    public boolean isEmpty() {
        return head.next.getReference() == null;
    }

    private class Node {
        final T value;
        AtomicMarkableReference<Node> next;

        Node() {
            value = null;
            next = new AtomicMarkableReference<>(null, false);
        }

        Node(T value) {
            this.value = value;
            next = new AtomicMarkableReference<>(null, false);
        }

    }
}
