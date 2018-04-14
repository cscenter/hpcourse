import java.util.concurrent.atomic.AtomicMarkableReference;

import javafx.util.Pair;

public class LockFreeSetImpl<T extends Comparable<T>> implements LockFreeSet<T> {
    private final Node<T> head;
    private final Node<T> tail;

    public LockFreeSetImpl() {
        tail = new Node<>(null, null);
        head = new Node<>(null, tail);
    }

    @Override
    public boolean add(final T value) {
        while (true) {
            final Pair<Node<T>, Node<T>> pair = find(value);
            final Node<T> previous = pair.getKey();
            final Node<T> current = pair.getValue();
            if (current != tail && value.compareTo(current.getValue()) == 0) {
                return false;
            }
            final Node<T> node = new Node<>(value, current);
            if (previous.compareAndSetNext(current, node)) {
                return true;
            }
        }
    }

    @Override
    public boolean remove(final T value) {
        while (true) {
            final Pair<Node<T>, Node<T>> pair = find(value);
            final Node<T> previous = pair.getKey();
            final Node<T> current = pair.getValue();
            final T currValue = current.getValue();
            if (current == tail || value.compareTo(currValue) != 0) {
                return false;
            }
            final Node<T> following = current.getNext();
            if (!current.tryToMark()) {
                continue;
            }
            previous.compareAndSetNext(current, following);
            return true;
        }
    }

    @Override
    public boolean contains(final T value) {
        Node<T> current = head.getNext();
        T currValue = current.getValue();
        while (current != tail && value.compareTo(currValue) > 0) {
            current = current.getNext();
            currValue = current.getValue();
        }
        if (current == tail) {
            return false;
        }
        return value.compareTo(currValue) == 0 && !current.isMarkedForDelete();
    }

    @Override
    public boolean isEmpty() {
        return head.compareAndSetNext(tail, tail);
    }

    private Pair<Node<T>, Node<T>> find(final T key) {
        while (true) {
            Node<T> previous = head;
            Node<T> current = previous.getNext();
            while (current != tail) {
                final Node<T> following = current.getNext();
                if (current.isMarkedForDelete() && !previous.compareAndSetNext(current, following)) {
                    break;
                }
                if (isKeyLeCurrent(key, current)) {
                    return new Pair<>(previous, current);
                } 
                previous = current;
                current = following;
            }
            if (current == tail) {
                return new Pair<>(previous, tail);
            }
        }
    }

    private boolean isKeyLeCurrent(final T key, final Node<T> current) {
        return key.compareTo(current.getValue()) <= 0;
    }

    private static final class Node<T extends Comparable<T>> {
        private static final boolean NOT_DELETED = false;
        private static final boolean DELETED = true;

        private final T value;
        private final AtomicMarkableReference<Node<T>> next;

        private Node(final T value, final Node<T> next){
            this.value = value;
            this.next = new AtomicMarkableReference<>(next, NOT_DELETED);
        }

        private T getValue() {
            return value;
        }

        private Node<T> getNext() {
            return next.getReference();
        }

        private boolean isMarkedForDelete() {
            return next.isMarked();
        }

        boolean tryToMark() {
            final Node<T> node = next.getReference();
            return compareAndSetNext(node, node, NOT_DELETED, DELETED);
        }

        boolean compareAndSetNext(final Node<T> expected,
                                  final Node<T> newReference) {
            final boolean mark = isMarkedForDelete();
            return compareAndSetNext(expected, newReference, mark, mark);
        }

        boolean compareAndSetNext(final Node<T> expected,
                                  final Node<T> newReference,
                                  final boolean expectedMark,
                                  final boolean newMark) {
            return next.compareAndSet(expected, newReference, expectedMark, newMark);
        }
    }
}
