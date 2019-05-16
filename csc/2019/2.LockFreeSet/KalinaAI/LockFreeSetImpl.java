import java.util.HashSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicStampedReference;

public class LockFreeSetImpl<T extends Comparable<T>> implements LockFreeSet<T> {
    private Node<T> head;

    LockFreeSetImpl() {
        head = new Node<>(null, null);
    }

    @Override
    public boolean add(T value) {
        while (true) {
            Pair<T> pair = find(value);
            if (pair.right != null && value.compareTo(pair.right.value) == 0)
                return false;
            Node<T> toInsert = new Node<>(value, pair.right);

            int version = pair.left.next.getStamp();
            if (version != 0 && pair.left.next.compareAndSet(pair.right, toInsert, version, version + 1)) {
                return true;
            }
        }
    }

    @Override
    public boolean remove(T value) {
        while (true) {
            Pair<T> pair = find(value);
            if (pair.right == null || pair.right.value.compareTo(value) != 0)
                return false;
            Node<T> suc = pair.right.next.getReference();

            int version = pair.right.next.getStamp();
            if (version == 0) return false;
            if (!pair.right.next.compareAndSet(suc, suc, version, 0))
                continue;

            version = pair.left.next.getStamp();
            if (version != 0)
                pair.left.next.compareAndSet(pair.right, suc, version, version + 1);
            return true;
        }
    }

    @Override
    public boolean contains(T value) {
        Node<T> cur = head.next.getReference();
        while(cur != null && cur.value.compareTo(value) < 0)
            cur = cur.next.getReference();

        return cur != null && cur.value.compareTo(value) == 0 && cur.next.getStamp() != 0;
    }

    @Override
    public boolean isEmpty() {
        return !iterator().hasNext();
    }

    @Override
    public Iterator<T> iterator() {
        Set<IterValue<T>> prev = new HashSet<>();
        Set<IterValue<T>> cur = getValues();
        while (!cur.equals(prev)) {
            prev = cur;
            cur = getValues();
        }
        cur.removeIf(val -> val.value == null);

        final Set<IterValue<T>> values = cur;
        return new Iterator<T>() {
            private final Iterator<IterValue<T>> baseIterator = values.iterator();

            @Override
            public boolean hasNext() {
                return baseIterator.hasNext();
            }

            @Override
            public T next() {
                return baseIterator.next().value;
            }
        };
    }

    private Set<IterValue<T>> getValues() {
        Set<IterValue<T>> nodes = new HashSet<>();
        Node<T> cur = head;
        while(cur != null) {
            int version = cur.next.getStamp();
            if (version != 0)
                nodes.add(new IterValue<>(cur.value, version));
            cur = cur.next.getReference();
        }
        return nodes;
    }

    private Pair<T> find(T value) {
        retry:
        while (true) {
            Node<T> prev = head;
            Node<T> cur = head.next.getReference();
            Node<T> suc;
            while (cur != null) {
                suc = cur.next.getReference();
                if (cur.next.getStamp() == 0) {
                    int version = prev.next.getStamp();
                    if (version == 0 || !prev.next.compareAndSet(cur, suc, version, version + 1)) continue retry;
                    cur = suc;
                } else {
                    if (cur.value.compareTo(value) >= 0) return new Pair<>(prev, cur);
                    prev = cur;
                    cur = suc;
                }
            }
            return new Pair<>(prev, null);
        }
    }
}

class Node<T extends Comparable<T>> {
    final T value;
    final AtomicStampedReference<Node<T>> next;

    Node(T value, Node<T> next) {
        this.value = value;
        this.next = new AtomicStampedReference<>(next, 1);
    }
}

class Pair<T extends Comparable<T>> {
    final Node<T> left;
    final Node<T> right;

    Pair(Node<T> left, Node<T> right) {
        this.left = left;
        this.right = right;
    }
}

class IterValue<T extends Comparable<T>> {
    final T value;
    private final int version;

    IterValue(T val, int ver) {
        this.value = val;
        this.version = ver;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, version);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof IterValue)) return false;
        if (value == null && ((IterValue)o).value == null
                && version == ((IterValue)o).version) return true;
        return value != null && ((IterValue)o).value != null
                && value.equals(((IterValue)o).value) && version == ((IterValue)o).version;
    }
}