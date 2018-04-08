import java.util.concurrent.atomic.AtomicMarkableReference;

public class LockFreeSetImpl<T extends Comparable<T>> implements LockFreeSet<T> {

    private static class SimplePair<L, R> {

        final L first;
        final R second;

        private SimplePair(L first, R second) {
            this.first = first;
            this.second = second;
        }

        static <L, R> SimplePair<L, R> of(L first, R second) {
            return new SimplePair<>(first, second);
        }
    }

    private static class Node<T extends Comparable<T>> {

        final T value;
        final AtomicMarkableReference<Node<T>> markedNext;

        Node(final T value, final Node<T> next) {
            this.value = value;
            this.markedNext = new AtomicMarkableReference<>(next, false);
        }

        boolean isNextMarkedDeleted() {
            return markedNext.isMarked();
        }

        Node<T> next() {
            return markedNext.getReference();
        }

        int compareValueTo(T value) {
            return this.value.compareTo(value);
        }

        boolean equalsValue(T value) {
            return this.compareValueTo(value) == 0;
        }

    }

    private final Node<T> head = new Node<>(null, null);

    private SimplePair<Node<T>, Node<T>> find(T value) {
        while (true) {
            Node<T> prev = head;
            Node<T> curr = head.next();
            Node<T> next;
            while (true) {
                if (curr == null) {
                    return SimplePair.of(prev, null);
                }
                next = curr.next();
                if (curr.isNextMarkedDeleted()) {
                    if (!prev.markedNext.compareAndSet(curr, next, false, false)) {
                        break;
                    }
                } else if (curr.compareValueTo(value) >= 0) {
                    return SimplePair.of(prev, curr);
                }
                prev = curr;
                curr = next;

            }
        }


    }

    @Override
    public boolean add(T value) {
        while (true) {
            final SimplePair<Node<T>, Node<T>> pair = find(value);

            final Node<T> left = pair.first;
            final Node<T> right = pair.second;
            if (right != null && right.equalsValue(value)) {
                return false;
            } else if (left.markedNext.compareAndSet(right, new Node<>(value, right), false, false)) {
                return true;
            }
        }

    }

    @Override
    public boolean remove(T value) {
        while (true) {
            final SimplePair<Node<T>, Node<T>> pair = find(value);

            if (pair.second != null && pair.second.equalsValue(value)) {
                final Node<T> next = pair.second.next();
                if (pair.second.markedNext.attemptMark(next, true)) {
                    pair.first.markedNext.compareAndSet(pair.second, next, false, false);
                    return true;
                }
            } else
                return false;
        }
    }

    @Override
    public boolean contains(T value) {
        Node<T> curr = head.next();
        while (curr != null && curr.compareValueTo(value) < 0) {
            curr = curr.next();
        }
        return curr != null && !curr.isNextMarkedDeleted() && curr.equalsValue(value);
    }

    @Override
    public boolean isEmpty() {
        return head.next() == null;
    }
}
