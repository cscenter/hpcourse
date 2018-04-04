import java.util.concurrent.atomic.AtomicReference;

public class MyLockFreeImpl<T extends Comparable<T>> implements LockFreeSet<T> {

    private AtomicReference<Node> head;

    private class Node {
        Node(final T value, final AtomicReference<Node> next) {
            this.value = value;
            this.next = next;
        }

        final T value;
        AtomicReference<Node> next;
    }

    private class NodePair {
        NodePair(Node first, Node second) {
            this.first = first;
            this.second = second;
        }

        Node first;
        Node second;
    }

    public MyLockFreeImpl() {
        this.head = new AtomicReference<Node>(null);
    }

    NodePair findPair(T value) {
        Node crnt = head.get(), prev = null;

        while (null != crnt) {
            if (0 == crnt.value.compareTo(value))
                return new NodePair(crnt, prev);

            prev = crnt;
            crnt = crnt.next.get();
        }

        return new NodePair(crnt, prev);
    }

    @Override
    public boolean add(T value) {
        Node newNode = new Node(value, new AtomicReference<Node>(null));

        while (true) {
            NodePair finded = findPair(value);
            Node crnt = finded.first, prev = finded.second;

            // check:
            if (null != crnt) {
                assert 0 == crnt.value.compareTo(value);
                return false;
            }

            if (null == prev) {
                if (head.compareAndSet(null, newNode))
                    return true;
            }
            else {
                if (prev.next.compareAndSet(crnt, newNode))
                    return true;
            }
        }
    }

    @Override
    public boolean remove(T value) {
        while (true) {
            NodePair finded = findPair(value);
            Node crnt = finded.first, prev = finded.second;

            // check:
            if (null == crnt) {
                assert 0 != crnt.value.compareTo(value);
                return false;
            }

            Node next = crnt.next.get();
            if (null == prev) {
                if (head.compareAndSet(crnt, next))
                    return true;
            }
            else {
                if (prev.next.compareAndSet(crnt, next))
                    return true;
            }
        }
    }

    @Override
    public boolean contains(T value) {
        Node crnt = findPair(value).first;
        if (null != crnt) {
            assert 0 == crnt.value.compareTo(value);
            return true;
        }

        return false;
    }

    @Override
    public boolean isEmpty() {
        return null == head.get();
    }
}

