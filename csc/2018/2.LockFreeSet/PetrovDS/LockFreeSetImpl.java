import java.util.concurrent.atomic.AtomicMarkableReference;

public class LockFreeSetImpl<T extends Comparable<T>> implements LockFreeSet<T> {

    private final AtomicMarkableReference<Node> head;

    {
        this.head = new AtomicMarkableReference<>(null, false);
    }

    private class Node {
        Node(T value, AtomicMarkableReference<Node> next) {
            this.value = value;
            this.next = next;
        }

        final T value;
        final AtomicMarkableReference<Node> next;
    }

    private class NodePair {
        NodePair(Node first, Node second) {
            this.first = first;
            this.second = second;
        }

        final Node first;
        final Node second;
    }

    private NodePair findPair(T value) {
        Node crnt = head.getReference(), prev = null;
        AtomicMarkableReference<Node> ref = head;

        while (null != crnt) {

            // try clean:
            if (ref.compareAndSet(crnt, crnt.next.getReference(), true, crnt.next.isMarked())) {
                crnt = crnt.next.getReference();
                continue;
            }

            if (0 == crnt.value.compareTo(value) && !ref.isMarked())
                return new NodePair(crnt, prev);


            ref = crnt.next;
            prev = crnt;
            crnt = crnt.next.getReference();
        }

        return new NodePair(null, prev);
    }

    @Override
    public boolean add(T value) {
        Node newNode = new Node(value, new AtomicMarkableReference<>(null, false));

        while (true) {
            NodePair found = findPair(value);
            Node crnt = found.first, prev = found.second;

            if (null != crnt)
                return false;

            if (null == prev) {
                if (head.compareAndSet(null, newNode, false, false))
                    return true;
            } else {
                if (prev.next.compareAndSet(null, newNode, false, false))
                    return true;
            }
        }
    }

    @Override
    public boolean remove(T value) {
        while (true) {
            NodePair found = findPair(value);
            Node crnt = found.first, prev = found.second;

            if (null == crnt)
                return false;

            Node next = crnt.next.getReference();
            if (null == prev) {
                if (head.attemptMark(crnt, true)) {
                    head.compareAndSet(crnt, next, true, crnt.next.isMarked());
                    return true;
                }
            }
            else {
                if (prev.next.attemptMark(crnt, true)) {
                    prev.next.compareAndSet(crnt, next, true, crnt.next.isMarked());
                    return true;
                }
            }
        }
    }

    @Override
    public boolean contains(T value) {
        Node crnt = findPair(value).first;

        if (null != crnt)
            return true;

        return false;
    }

    @Override
    public boolean isEmpty() {
        return null == head.getReference();
    }
}

