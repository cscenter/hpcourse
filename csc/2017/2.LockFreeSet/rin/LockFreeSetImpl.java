import java.util.concurrent.atomic.AtomicMarkableReference;

/**
 * @author Ivan Rudakov
 */

public class LockFreeSetImpl<T extends Comparable<T>> implements LockFreeSet<T> {
    private final Node head = new Node(null, null);

    @Override
    public boolean add(T value) {
        while (true) {
            Pair pair = find(value);
            Node prev = pair.first;
            Node curr = pair.second;
            if (curr != null && curr.value.compareTo(value) == 0) {
                return false;
            }
            Node newNode = new Node(value, curr);
            if (prev.next.compareAndSet(curr, newNode, false, false)) {
                return true;
            }
        }
    }

    @Override
    public boolean remove(T value) {
        while (true) {
            Pair pair = find(value);
            Node curr = pair.second;
            if (curr == null || curr.value.compareTo(value) != 0) {
                return false;
            }
            if (curr.next.attemptMark(curr.next.getReference(), true)) {
                return true;
            }
        }
    }

    @Override
    public boolean contains(T value) {
        Node curr = head.next.getReference();
        while (curr != null && curr.value.compareTo(value) < 0) {
            curr = curr.next.getReference();
        }
        return curr != null && curr.value.compareTo(value) == 0 && !curr.next.isMarked();
    }

    @Override
    public boolean isEmpty() {
        return head.next.getReference() == null;
    }

    private Pair find(T value) {
        while (true) {
            Node prev = head;
            Node curr = prev.next.getReference();
            while (true) {
                if (curr == null) {
                    return new Pair(prev, null);
                }
                Node next = curr.next.getReference();
                if (curr.next.isMarked()) {
                    if (!prev.next.compareAndSet(curr, next, false, false)) {
                        break;
                    }
                } else {
                    if (curr.value.compareTo(value) >= 0) {
                        return new Pair(prev, curr);
                    }
                    prev = curr;
                }
                curr = next;
            }
        }
    }

    private class Node {
        final T value;
        final AtomicMarkableReference<Node> next;

        Node(final T value, final Node next) {
            this.value = value;
            this.next = new AtomicMarkableReference<>(next, false);
        }
    }

    private class Pair {
        final Node first;
        final Node second;

        Pair(final Node first, final Node second) {
            this.first = first;
            this.second = second;
        }
    }
}
