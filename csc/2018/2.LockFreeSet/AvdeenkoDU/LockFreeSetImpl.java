//As a reference I used "The Art of Multiprocessor Programming"
import java.util.concurrent.atomic.AtomicMarkableReference;

public class LockFreeSetImpl<T extends Comparable<T>> implements LockFreeSet<T> {

    private Node head;

    LockFreeSetImpl() {
        this.head = new Node();
    }

    @Override
    public boolean add(T value) {
        while (true) {
            Pair pair = search(head, value);
            Node previous = pair.previous;
            Node current = pair.current;
            if (current != null && current.value.compareTo(value) == 0) {
                return false;
            } else {
                Node node = new Node(value, current);
                if (previous.next.compareAndSet(current, node, false, false)) {
                    return true;
                }
            }
        }
    }

    @Override
    public boolean remove(T value) {
        while (true) {
            Pair pair = search(head, value);
            Node previous = pair.previous;
            Node current = pair.current;
            if (current == null || current.value.compareTo(value) != 0) {
                return false;
            } else {
                Node next = current.next.getReference();
                if (!current.next.attemptMark(next, true))
                    continue;
                previous.next.compareAndSet(current, next, false, false);
                return true;
            }
        }
    }

    @Override
    public boolean contains(T value) {
        boolean marked[] = {false};
        Node current = head.next.getReference();
        while (current != null && current.value.compareTo(value) < 0) {
            current = current.next.get(marked);
        }
        return current != null && current.value.compareTo(value) == 0 && !marked[0];
    }

    @Override
    public boolean isEmpty() {
        return head.next.getReference() == null;
    }


    public Pair search(Node head, T value) {
        Node previous, current, next;
        boolean[] marked = {false};
        retry:
        while (true) {
            previous = head;
            current = previous.next.getReference();
            while (current != null) {
                next = current.next.get(marked);
                while (marked[0]) {
                    if (!previous.next.compareAndSet(current, next, false, false))
                        continue retry;
                    current = next;
                    next = current.next.get(marked);
                }
                if (current.value.compareTo(value) >= 0)
                    return new Pair(previous, current);
                previous = current;
                current = next;
            }
            return new Pair(previous, null);
        }
    }

    private class Node {
        private  T value;
        private  AtomicMarkableReference<Node> next;

        Node() {
            this.value = null;
            this.next = new AtomicMarkableReference<>(null, false);
        }

        Node(T value, Node next) {
            this.value = value;
            this.next = new AtomicMarkableReference<>(next, false);
        }
    }

    class Pair {
        Node previous, current;

        Pair(Node previous, Node current) {
            this.previous = previous;
            this.current = current;
        }
    }
}

