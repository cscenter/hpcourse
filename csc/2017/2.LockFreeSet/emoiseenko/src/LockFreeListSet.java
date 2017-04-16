import java.util.concurrent.atomic.AtomicMarkableReference;
import java.util.concurrent.atomic.AtomicReference;

public class LockFreeListSet<T extends Comparable<T>> implements LockFreeSet<T> {

    private static class Node<V extends Comparable<V>> {
        public V value;
        public AtomicMarkableReference<Node<V>> next;

        public Node(V v, Node<V> n) {
            value = v;
            next = new AtomicMarkableReference<>(n, false);
        }
    }

    private static class Pair<V extends Comparable<V>> {
        public Node<V> pred;
        public Node<V> curr;

        public Pair(Node<V> a, Node<V> b) {
            pred = a;
            curr = b;
        }
    }

    private Node<T> head;

    public LockFreeListSet() {
        head = new Node<T>(null, new Node<T>(null, null));
    }

    public Pair<T> find(T value) {
        retry: while (true) {
            Node<T> pred = head;
            Node<T> curr = pred.next.getReference();
            while (true) {
                boolean[] mark = {false};
                Node<T> succ = curr.next.get(mark);
                while (mark[0]) {
                    boolean removed = pred.next.compareAndSet(curr, succ, false, false);
                    if (!removed) {
                        continue retry;
                    }
                    curr = succ;
                    succ = curr.next.get(mark);
                }
                if (curr.value == null || value.compareTo(curr.value) <= 0) {
                    return new Pair<>(pred, curr);
                }
                pred = curr;
                curr = succ;
            }
        }
    }

    public boolean add(T value) {
        while (true) {
            Pair<T> pair = find(value);
            Node<T> pred = pair.pred;
            Node<T> curr = pair.curr;
            if (curr.value == null || value.compareTo(curr.value) != 0) {
                Node<T> node = new Node<T>(value, curr);
                boolean added = pred.next.compareAndSet(curr, node, false, false);
                if (added) {
                    return true;
                }
            } else {
                return false;
            }
        }
    }

    public boolean remove(T value) {
        while (true) {
            Pair<T> pair = find(value);
            Node<T> pred = pair.pred;
            Node<T> curr = pair.curr;
            if (curr.value != null && value.compareTo(curr.value) == 0) {
                Node<T> succ = curr.next.getReference();
                boolean marked = curr.next.attemptMark(succ, true);
                if (!marked) {
                    continue;
                }
                pred.next.compareAndSet(curr, succ, false, false);
                return true;
            } else {
                return false;
            }
        }
    }

    public boolean contains(T value) {
        boolean[] mark = {false};
        Node<T> curr = head.next.get(mark);
        while (curr.value != null && curr.value.compareTo(value) < 0) {
            curr = curr.next.get(mark);
        }
        return !mark[0] && curr.value != null && curr.value.compareTo(value) == 0;
    }

    public boolean isEmpty() {
        Node<T> snd = head.next.getReference();
        return snd.value == null;
    }
}
