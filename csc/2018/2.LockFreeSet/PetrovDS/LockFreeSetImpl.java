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

            if (crnt.value.compareTo(value) <= 0 && !ref.isMarked())
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

            if (null != crnt && 0 == crnt.value.compareTo(value))
                return false;

            if (null == prev) {
                newNode.next.set(crnt, head.isMarked());

                if (head.compareAndSet(crnt, newNode, false, false))
                    return true;
            }
            else {
                newNode.next.set(crnt, prev.next.isMarked());

                if (prev.next.compareAndSet(crnt, newNode, false, false))
                    return true;
            }
        }
    }

    @Override
    public boolean remove(T value) {
        while (true) {
            NodePair found = findPair(value);
            Node crnt = found.first, prev = found.second;

            if (null == crnt || 0 != crnt.value.compareTo(value))
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
        AtomicMarkableReference<Node> ref = head;
        Node crnt = ref.getReference();

        while (null != crnt) {
            if (0 == crnt.value.compareTo(value) && !ref.isMarked())
                return true;

            ref = crnt.next;
            crnt = crnt.next.getReference();
        }

        return false;
    }

    @Override
    public boolean isEmpty() {
        return null == head.getReference();
    }
}