import javafx.util.Pair;

import java.nio.channels.NonReadableChannelException;
import java.util.concurrent.atomic.AtomicMarkableReference;


public class LockFreeSetImpl<T extends Comparable<T>> implements LockFreeSet<T> {
    private class Node {
        Node(T value, Node next) {
            this.value = value;
            this.next = new AtomicMarkableReference<>(next, false);
        }

        final T value;
        final AtomicMarkableReference<Node> next;
    }

    final private AtomicMarkableReference<Node> head = new AtomicMarkableReference<>(null, false);

    @Override
    public boolean add(T value) {
        Node newNode = new Node(value, null);

        while (true) {
            Pair<AtomicMarkableReference<Node>, Node> pair = find(value);
            AtomicMarkableReference<Node> curr = pair.getKey();
            Node next = pair.getValue();

            if (next != null && next.value.equals(value) && !next.next.isMarked()) {
                return false;
            }

            newNode.next.set(next, false);

            if (curr.compareAndSet(next, newNode, false, false))
                return true;
        }
    }

    @Override
    public boolean remove(T value) {
        while (true) {
            Pair<AtomicMarkableReference<Node>, Node> pair = find(value);
            AtomicMarkableReference<Node> prev = pair.getKey();
            Node curr = pair.getValue();

            if (curr == null || !curr.value.equals(value))
                return false;

            Node next = curr.next.getReference();

            if (!curr.next.compareAndSet(next, next, false, true))
                continue;

            prev.compareAndSet(curr, next, false, false);

            return true;
        }
    }

    @Override
    public boolean contains(T value) {
        Node node = head.getReference();
        while (node != null && node.value.compareTo(value) < 0)
            node = node.next.getReference();

        return node != null && node.value.equals(value) && !node.next.isMarked();
    }

    private Pair<AtomicMarkableReference<Node>, Node> find(T value) {
        while (true) {
            AtomicMarkableReference<Node> curr = head;
            Node next = head.getReference();

            while (true) {
                if (next == null)
                    return new Pair<>(curr, null);

                if (next.next.isMarked()) {
                    if (!curr.compareAndSet(next, next.next.getReference(), false, false))
                        break;
                    next = next.next.getReference();
                } else {
                    if (curr.getReference().value.compareTo(value) >= 0)
                        return new Pair<>(curr, next);
                    curr = next.next;
                    next = curr.getReference();
                }
            }
        }
    }

    public boolean isEmpty() {
        return head.getReference() == null;
    }
}
