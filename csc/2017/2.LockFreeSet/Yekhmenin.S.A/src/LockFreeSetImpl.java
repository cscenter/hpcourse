import javafx.util.Pair;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicMarkableReference;

public class LockFreeSetImpl<T extends Comparable<T>> implements LockFreeSet<T> {
    private Node tail;
    private Node head;
    private AtomicInteger size;

    public LockFreeSetImpl() {
        size = new AtomicInteger(0);
        tail = new Node();
        head = new Node();
        head.next.set(tail, false);
    }

    @Override
    public boolean add(T value) {
        while (true) {
            Pair<Node, Node> pair = find(value);
            Node pred = pair.getKey();
            Node curr = pair.getValue();

            if (curr.value == value)
                return false;

            Node node = new Node(value);
            node.next.set(curr, false);
            if (pred.next.compareAndSet(curr, node, false, false)) {
                size.incrementAndGet();
                return true;
            }
        }
    }

    @Override
    public boolean remove(T value) {
        while (true) {
            Pair<Node, Node> pair = find(value);
            Node pred = pair.getKey();
            Node curr = pair.getValue();

            if (curr.value != value)
                return false;

            Node succ = curr.next.getReference();
            if (!curr.next.attemptMark(succ, true))
                continue;
            pred.next.compareAndSet(curr, succ, false, false);
            size.decrementAndGet();
            return true;
        }
    }

    @Override
    public boolean contains(T value) {
        Pair<Node, Node> pair = find(value);
        return pair.getValue().value == value;
    }

    @Override
    public boolean isEmpty() {
        return size.get() == 0;
    }

    private Pair<Node, Node> find(T key) {
        retry:
        while (true) {
            Node pred = head;
            Node curr = pred.next.getReference();
            boolean[] markHolder = new boolean[1];

            while (true) {
                Node succ = curr.next.get(markHolder);
                if (markHolder[0]) {
                    if (!pred.next.compareAndSet(curr, succ, false, false))
                        continue retry;
                    curr = succ;
                } else {
                    if (curr.value == null || curr.value.compareTo(key) >= 0)
                        return new Pair<>(pred, curr);
                    pred = curr;
                    curr = succ;
                }
            }
        }
    }

    private class Node {
        final T value;
        final AtomicMarkableReference<Node> next;

        public Node(T value) {
            this.value = value;
            next = new AtomicMarkableReference<>(null, false);
        }

        public Node() {
            this(null);
        }
    }
}
