package TuryshevaK;

import java.util.concurrent.atomic.AtomicMarkableReference;

public class LockFreeSetImpl<T extends Comparable<T>> implements LockFreeSet<T> {


    private class Node<T extends  Comparable> {
        public final T value;
        public final AtomicMarkableReference<Node<T>> next;

        public Node(T value, Node<T> next) {
            this.value = value;
            this.next = new AtomicMarkableReference<Node<T>>(next, false);
        }
    }

    private class Pair {
        public final Node<T> prev;
        public final Node<T> curr;

        public Pair(Node<T> prev, Node<T> curr) {
            this.prev = prev;
            this.curr = curr;
        }
    }

    private final Node<T> head;

    public LockFreeSetImpl() {
        head = new Node<T>(null, null);

    }

    private Pair findValue(final T value) {
        while (true) {
            Node<T> prev = head;
            Node<T> curr = head.next.getReference();
            Node<T> next;
            while (true) {
                if (curr != null) {
                    next = curr.next.getReference();
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
                } else {
                    return new Pair(prev, curr);
                }
            }

        }
    }

    public boolean add(final T value) {
        while (true) {
            final Pair pair = findValue(value);
            final Node prev = pair.prev;
            final Node curr = pair.curr;

            if (curr != null && curr.value.compareTo(value) == 0) {
                return false;
            }
            final Node node = new Node(value, curr);
            if (prev.next.compareAndSet(curr, node, false, false)) {
                return true;
            }
        }


    }

    public boolean remove(final T value) {
        while (true) {
            final Pair pair = findValue(value);
            final Node prev = pair.prev;
            final Node curr = pair.curr;
            if (curr == null || curr.value.compareTo(value) != 0) {
                return false;
            }
            final Node<T> next = (Node<T>) curr.next.getReference();
            if (!curr.next.attemptMark(next, true)) {
                continue;
            } else {
                prev.next.compareAndSet(curr, next, false, false);
                return true;
            }

        }
    }

    public boolean contains(final T value) {
        Node<T> curr = head.next.getReference();
        while (curr != null && curr.value.compareTo(value) < 0) {
            curr = curr.next.getReference();
        }
        return curr != null && !curr.next.isMarked() && curr.value.compareTo(value) == 0;
    }

    public boolean isEmpty() {
        return head.next.getReference() == null;
    }
}
