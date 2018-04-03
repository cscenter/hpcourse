import java.util.concurrent.atomic.AtomicMarkableReference;

public class LockFreeSetImpl<T extends Comparable<T>> implements LockFreeSet<T> {

    private Node head;

    public LockFreeSetImpl() {
        this.head = new Node(null, new AtomicMarkableReference<>(null, false));
    }

    @Override
    public boolean add(T value) {
        while (true) {
            NodePair window = find(value);
            if (window.second != null && window.second.value.compareTo(value) == 0) return false;
            //newNode -> window.second
            Node newNode = new Node(value, new AtomicMarkableReference<>(window.second, false));
            //if window.first still exist set window.first -> newNode
            if (window.first.nextAndState.compareAndSet(window.second, newNode, false, false)) return true;
        }
    }

    private NodePair find(T val) {
        retry:
        while (true) {
            Node pred = head;
            Node cur = pred.nextAndState.getReference();

            while (cur != null) {
                Node next = cur.nextAndState.getReference();

                //if сurrent element removed logically - try to remove if physically
                if (cur.nextAndState.isMarked()) {
                    if (!pred.nextAndState.compareAndSet(cur, next, false, false))
                        continue retry;
                    cur = next;
                } else {
                    if (cur.value.compareTo(val) >= 0) return new NodePair(pred, cur);
                    pred = cur;
                    cur = next;
                }
            }

            return new NodePair(pred, null);
        }
    }

    @Override
    public boolean remove(T value) {
        NodePair window = find(value);
        if (window.second.getValue().compareTo(value) != 0) return false;

        while (true) {
            Node next = window.second.nextAndState.getReference();

            //remove logical
            if (!window.second.nextAndState.attemptMark(next, true)) continue;

            //attempt to physical remove
            window.first.nextAndState.compareAndSet(window.second, next, false, false);
            return true;

        }
    }

    @Override
    public boolean contains(T value) {
        Node cur = head.nextAndState.getReference();
        while (cur != null) {
            if (cur.value.compareTo(value) >= 0) break;
            cur = cur.nextAndState.getReference();
        }

        return cur != null && cur.value.compareTo(value) == 0 && !cur.nextAndState.isMarked();
    }

    @Override
    public boolean isEmpty() {
        return head.nextAndState.getReference() == null;
    }

    private class NodePair {
        private final Node first;
        private final Node second;

        private NodePair(Node first, Node second) {
            this.first = first;
            this.second = second;
        }
    }

    private class Node {
        private final T value;

        private final AtomicMarkableReference<Node> nextAndState;

        private Node(T value, AtomicMarkableReference<Node> nextAndState) {
            this.value = value;
            this.nextAndState = nextAndState;
        }

        public T getValue() {
            return value;
        }

        public AtomicMarkableReference<Node> getNextAndState() {
            return nextAndState;
        }
    }
}
