package bzik;

import java.util.concurrent.atomic.AtomicMarkableReference;

public class LockFreeSetImpl<T extends Comparable<T>> implements LockFreeSet<T> {
    public boolean add(T value) {
        while (true) {
            SearchResult nodes = find(value);
            if (nodes.current != null && nodes.current.value.equals(value)) {
                return false;
            }
            if (nodes.previous.nextReference.compareAndSet(nodes.current,
                    new Node(value, nodes.current), false, false)) {
                return true;
            }
        }
    }

    public boolean remove(T value) {
        while (true) {
            SearchResult nodes = find(value);
            if (nodes.current == null || !nodes.current.value.equals(value)) {
                return false;
            }
            Node tail = nodes.current.nextReference.getReference();
            if (!nodes.current.nextReference.attemptMark(tail, true)) {
                continue;
            }
            nodes.previous.nextReference.compareAndSet(nodes.current, tail, false, false);
            return true;
        }
    }

    public boolean contains(T value) {
        Node local = head.nextReference.getReference();
        while (local != null && local.value.compareTo(value) < 0) {
            local = local.nextReference.getReference();
        }
        return local != null && local.value.compareTo(value) == 0 && !local.nextReference.isMarked();
    }

    public boolean isEmpty() {
        return head.nextReference.getReference() == null;
    }

    private SearchResult find(T value) {
        Node previous = head;
        Node current = head.nextReference.getReference();
        while (current != null) {
            Node local = current.nextReference.getReference();
            if (current.nextReference.isMarked()) {
                if (!previous.nextReference.compareAndSet(current, local, false, false)) {
                    return find(value);
                }
            } else {
                if (current.value.compareTo(value) >= 0) {
                    return new SearchResult(previous, current);
                }
                previous = current;
            }
            current = local;
        }
        return new SearchResult(previous, null);
    }

    private final Node head = new Node(null, null);

    private class Node {
        final T value;
        final AtomicMarkableReference<Node> nextReference;

        Node(T value, Node next) {
            this.value = value;
            nextReference = new AtomicMarkableReference<>(next, false);
        }
    }

    private class SearchResult {
        final Node previous;
        final Node current;

        SearchResult(Node previous, Node current) {
            this.previous = previous;
            this.current = current;
        }
    }
}