import java.util.concurrent.atomic.AtomicMarkableReference;

public class LockFreeSetImpl<T extends Comparable<T>> implements LockFreeSet<T> {

    public class Node {
        final AtomicMarkableReference<Node> next = new AtomicMarkableReference<>(null, false);
        final T value;

        Node(T value) {
            this.value = value;
        }
    }

    private Node end = new Node( null);
    private Node begin = new Node(null);

    public LockFreeSetImpl() {
        begin.next.set(end, false);
    }

    Node findNearestLeft(T value) {
        Node left = begin;
        Node current = begin.next.getReference();

        while (current != end) {
            Node right = current.next.getReference();

            if (current.next.isMarked()) {
                if (!left.next.compareAndSet(current, right, false, false)) {
                    continue;
                }
                current = right;
            }

            if (current.value.compareTo(value) >= 0) {
                return left;
            }
            left = current;
            current = current.next.getReference();
        }

        return left;
    }

    @Override
    public boolean add(T value) {
        Node new_node = new Node(value);

        while (true) {
            Node left = findNearestLeft(value);
            Node current = left.next.getReference();

            if (current.value != null && current.value.compareTo(value) == 0) {
                return false;
            }

            new_node.next.set(current, false);

            if (left.next.compareAndSet(current, new_node, false, false)) {
                return true;
            }
        }
    }

    @Override
    public boolean remove(T value) {
        while (true) {
            Node left = findNearestLeft(value);
            Node current = left.next.getReference();

            if (current.value != null && current.value.compareTo(value) != 0) {
                return false;
            }

            Node right = current.next.getReference();

            if (right == null) {
                return false;
            }

            if (!current.next.compareAndSet(right, right, false, true)) {
                continue;
            }

            if (left.next.compareAndSet(current, right, false, false)) {
                return true;
            }
        }
    }

    @Override
    public boolean contains(T value) {
        Node current = begin.next.getReference();

        while (current != end) {
            if (current.value.compareTo(value) == 0) {
                return !current.next.isMarked();
            }
            current = current.next.getReference();
        }

        return false;
    }

    @Override
    public boolean isEmpty() {
        Node current = begin.next.getReference();

        while (current != end) {
            if (!current.next.isMarked()) {
                return false;
            }
            current = current.next.getReference();
        }

        return true;
    }
}
