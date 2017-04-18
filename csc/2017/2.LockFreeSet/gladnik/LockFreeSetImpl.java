package compscicenter.hpcourse.hw2;

import java.util.List;
import java.util.concurrent.atomic.AtomicMarkableReference;

import static java.util.Arrays.asList;

/**
 * @author gladnik (Nikolai Gladkov)
 */
public class LockFreeSetImpl<T extends Comparable<T>> implements LockFreeSet<T> {

    private class Node {
        private final T value;
        private final AtomicMarkableReference<Node> next;

        Node(T value, Node next) {
            this.value = value;
            this.next = new AtomicMarkableReference<>(next, false);
        }
    }

    private final Node head = new Node(null, null);

    @Override
    public boolean add(T value) {
        while (true) {
            List<Node> results = searchExisting(value);
            Node prev = results.get(0);
            Node current = results.get(1);
            boolean haveSuchElement = current != null && current.value.equals(value);
            if (haveSuchElement) {
                return false;
            } else if (prev.next.compareAndSet(current, new Node(value, current), false, false)) {
                return true;
            }
        }
    }

    @Override
    public boolean remove(T value) {
        while (true) {
            if (this.isEmpty()) {
                return false;
            }
            List<Node> results = searchExisting(value);
            Node prev = results.get(0);
            Node current = results.get(1);
            boolean haveNoElement = current == null || !current.value.equals(value);
            if (haveNoElement) {
                return false;
            } else {
                Node nextNodeRef = current.next.getReference();
                if (current.next.attemptMark(nextNodeRef, true)) {
                    prev.next.compareAndSet(current, nextNodeRef, false, false);
                    return true;
                }
            }
        }
    }

    @Override
    public boolean contains(T value) {
        boolean[] isCurrentDeleted = {false};
        Node current = head.next.get(isCurrentDeleted);
        while (current != null && (current.value.compareTo(value) < 0 || isCurrentDeleted[0])) {
            current = current.next.get(isCurrentDeleted);
        }
        return current != null && value.equals(current.value) && !isCurrentDeleted[0];
    }

    @Override
    public boolean isEmpty() {
        return head.next.getReference() == null;
    }


    private List<Node> searchExisting(T value) {
        searchStart:
        while (true) {
            Node prev = head;
            Node current = head.next.getReference();
            while (true) {
                if (current != null) {
                    boolean[] isNextDeleted = {false};
                    Node next = current.next.get(isNextDeleted);
                    while (isNextDeleted[0]) {
                        if (!prev.next.compareAndSet(current, next, false, false)) {
                            continue searchStart;
                        }
                        current = next;
                        next = current.next.get(isNextDeleted);
                    }
                    if (prev.next.isMarked() || prev.next.getReference() != current) {
                        continue searchStart;
                    }
                    if (current.value.compareTo(value) >= 0) {
                        return asList(prev, current);
                    }
                    prev = current;
                    current = current.next.getReference();
                } else {
                    return asList(prev, null);
                }
            }
        }
    }
}
