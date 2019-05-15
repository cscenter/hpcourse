import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicMarkableReference;

public final class LockFreeSetImpl<T extends Comparable<T>> implements LockFreeSet<T> {

    private final Node<T> tail = new Node<>();
    private final Node<T> head = new Node<>(null, tail);

    @Override
    public boolean add(T value) {
        while (true) {
            NodePair<T> place = findPlace(value);
            Node<T> prev = place.prev;
            Node<T> curr = place.curr;

            // if already exists
            if (Objects.equals(curr.value, value)) {
                return false;
            }

            // create new node and try to insert
            Node<T> newNode = new Node<>(value, curr);
            if (prev.next.compareAndSet(curr, newNode, false, false)) {
                return true;
            }
        }
    }

    @Override
    public boolean remove(T value) {
        while (true) {
            NodePair<T> place = findPlace(value);
            Node<T> prev = place.prev;
            Node<T> curr = place.curr;

            // if not presented in the set
            if (!Objects.equals(curr.value, value)) {
                return false;
            }

            Node<T> next = curr.next.getReference();
            // try to delete current logically
            if (!curr.next.compareAndSet(next, next, false, true)) {
                continue;
            }
            // delete current physically
            prev.next.compareAndSet(curr, next, false, false);
            return true;
        }
    }

    @Override
    public boolean contains(T value) {
        Node<T> curr = head.next.getReference();
        while (curr != tail && curr.value.compareTo(value) < 0) {
            curr = curr.next.getReference();
        }
        return Objects.equals(curr.value, value) && !curr.next.isMarked();
    }

    @Override
    public boolean isEmpty() {
        Node<T> curr = head.next.getReference();
        while (curr != tail && curr.next.isMarked()) {
            curr = curr.next.getReference();
        }
        return curr == tail;
    }

    @Override
    public Iterator<T> iterator() {
        return null;
    }

    private NodePair<T> findPlace(T value) {
        while (true) {
            Node<T> prev = head;
            Node<T> curr = prev.next.getReference();
            while (true) {
                if (curr == tail) {
                    return new NodePair<>(prev, curr);
                }
                Node<T> next = curr.next.getReference();
                // if current is logically deleted
                if (curr.next.isMarked()) {
                    // try to delete it physically
                    if (!prev.next.compareAndSet(curr, next, false, false)) {
                        break;
                    }
                    curr = next;
                } else {
                    if (curr.value.compareTo(value) >= 0) {
                        return new NodePair<>(prev, curr);
                    }
                    prev = curr;
                    curr = next;
                }
            }
        }
    }

    private class Node<E> {
        final E value;
        final AtomicMarkableReference<Node<E>> next;

        Node(E value, Node<E> nextNode) {
            this.value = value;
            this.next = new AtomicMarkableReference<>(nextNode, false);
        }

        Node() {
            value = null;
            next = new AtomicMarkableReference<>(null, false);
        }
    }

    private class NodePair<E> {
        final Node<E> prev;
        final Node<E> curr;

        NodePair(Node<E> prev, Node<E> curr) {
            this.prev = prev;
            this.curr = curr;
        }
    }
}
