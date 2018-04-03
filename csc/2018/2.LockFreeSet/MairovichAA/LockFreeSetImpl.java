import static java.util.Objects.nonNull;

import java.util.concurrent.atomic.AtomicMarkableReference;

import javafx.util.Pair;

public class LockFreeSetImpl<T extends Comparable<T>> implements LockFreeSet<T> {
    private final Node<T> head;
    private final Node<T> tail;

    public LockFreeSetImpl() {
        head = new Node<>(null);
        tail = new Node<>(null);
        head.setNext(tail);
    }

    @Override
    public boolean add(final T value) {
        while (true) {
            final Pair<Node<T>, Node<T>> pair = find(value);
            final Node<T> pred = pair.getKey();
            final Node<T> curr = pair.getValue();
            if (nonNull(curr.getValue()) && value.compareTo(curr.getValue()) == 0) {
                return false;
            }
            final Node<T> node = new Node<>(value);
            node.setNext(curr);
            if (pred.compareAndSetNext(curr, node)) {
                return true;
            }
        }
    }

    @Override
    public boolean remove(final T value) {
        while (true) {
            final Pair<Node<T>, Node<T>> pair = find(value);
            final Node<T> pred = pair.getKey();
            final Node<T> curr = pair.getValue();
            final T currValue = curr.getValue();
            if (value.compareTo(currValue) != 0) {
                return false;
            }
            final Node<T> succ = curr.getNext();
            if (!curr.tryToMark()) {
                continue;
            }
            pred.compareAndSetNext(curr, succ);
            return true;
        }
    }

    @Override
    public boolean contains(final T value) {
        Node<T> curr = head.getNext();
        T currValue = curr.getValue();
        while (curr != tail && value.compareTo(currValue) > 0) {
            curr = curr.getNext();
            currValue = curr.getValue();
        }
        if (curr == tail) {
            return false;
        }
        return value.compareTo(currValue) == 0 && !curr.isMarkedForDelete();
    }

    @Override
    public boolean isEmpty() {
        return head.compareAndSetNext(tail, tail);
    }

    private Pair<Node<T>, Node<T>> find(final T key) {
        while (true) {
            Node<T> pred = head;
            Node<T> curr = pred.getNext();
            while (curr != tail) {
                final Node<T> succ = curr.getNext();
                if (curr.isMarkedForDelete() && !pred.compareAndSetNext(curr, succ)) {
                    break;
                }
                if (key.compareTo(curr.getValue()) <= 0) {
                    return new Pair<>(pred, curr);
                } else {
                    pred = curr;
                }
                curr = succ;
            }
            if (curr == tail) {
                return new Pair<>(pred, curr);
            }
        }
    }

    private static final class Node<T extends Comparable<T>> {
        private static final boolean NOT_DELETED = false;
        private static final boolean DELETED = true;

        private final T value;
        private final AtomicMarkableReference<Node<T>> next;

        private Node(final T value) {
            this.value = value;
            next = new AtomicMarkableReference<>(null, NOT_DELETED);
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

        private void setNext(final Node<T> next) {
            this.next.set(next, NOT_DELETED);
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
