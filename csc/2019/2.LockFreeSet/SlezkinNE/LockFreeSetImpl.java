import javafx.util.Pair;
import java.util.*;
import java.util.concurrent.atomic.AtomicStampedReference;

public class LockFreeSetImpl<T extends Comparable<T>> implements LockFreeSet<T> {

    private final Node head;
    private int ver;

    public LockFreeSetImpl() { head = new Node(null, null); }

    private class Node {
        final T value;
        final AtomicStampedReference<Node> next;

        Node(T value, Node node) {
            this.value = value;
            this.next = new AtomicStampedReference<>(node, 1);
        }

        Node(Node node) {
            this.value = node.value;
            this.next = new AtomicStampedReference<>(node.next(), node.version());
        }

        Node next() { return next.getReference(); }
        int cmp(T val) { return value.compareTo(val); }
        int version() { return next.getStamp(); }
        boolean cas2(Node ref, int ver) { return next.compareAndSet(ref, ref, ver, 0); }
        boolean cas(Node expR, Node newR, int ver) { return next.compareAndSet(expR, newR, ver, ver + 1); }
        public int hashCode() { return Objects.hash(value, next.getStamp()); }

        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Node node = (Node) o;
            return Objects.equals(value, node.value) && Objects.equals(next.getStamp(), node.next.getStamp()) &&
                    Objects.equals(next.getReference(), node.next.getReference());
        }
    }

    private Pair<Node, Node> findByValue(T value) {
        while (true) {
            Node prev = head;
            Node curr = head.next();
            while (true) {
                if (curr == null)
                    return new Pair<>(prev, null);
                Node next = curr.next();
                if (curr.version() == 0) {
                    if ((ver = prev.version()) == 0 || !prev.cas(curr, next, ver)) break;
                    curr = next;
                } else {
                    if (curr.cmp(value) >= 0)
                        return new Pair<>(prev, curr);
                    prev = curr;
                    curr = next;
                }
            }
        }
    }

    @Override
    public boolean add(T value) {
        while (true) {
            Pair<Node, Node> pair = findByValue(value);
            Node prev = pair.getKey();
            Node curr = pair.getValue();
            if (curr != null && curr.cmp(value) == 0) return false;
            if ((ver = prev.version()) != 0 && prev.cas(curr, new Node(value, curr), ver)) return true;
        }
    }

    @Override
    public boolean remove(T value) {
        while (true) {
            Pair<Node, Node> pair = findByValue(value);
            Node prev = pair.getKey();
            Node curr = pair.getValue();
            if (curr == null || curr.cmp(value) != 0) return false;
            Node succ = curr.next();
            if ((ver = curr.version()) == 0) return false;
            if (!curr.cas2(succ, ver)) continue;
            if ((ver = prev.version()) != 0) prev.cas(curr, succ, ver);
            return true;
        }
    }

    @Override
    public boolean contains(T value) {
        Node node = head.next();
        while (node != null && node.cmp(value) < 0) node = node.next();
        return node != null && node.cmp(value) == 0 && node.version() != 0;
    }

    @Override
    public boolean isEmpty() { return !iterator().hasNext(); }

    @Override
    public Iterator<T> iterator() {
        Set<Node>[] att = new HashSet[2];
        att[0] = new HashSet<>();
        att[1] = new HashSet<>();
        int i = 0;
        while(true) {
            att[i].clear();
            Node now = head;
            while(now != null) {
                if (now.version() != 0)
                    att[i].add(new Node(now));
                now = now.next();
            }
            if (att[1-i].equals(att[i])) {
                final Set<Node> attf = att[i];
                return new Iterator<T>() {
                    private final Iterator<Node> baseIterator = attf.iterator();
                    public boolean hasNext() { return baseIterator.hasNext(); }
                    public T next() { return baseIterator.next().value; }};
            }
            i = 1 - i;
        }
    }
}