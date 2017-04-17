import java.util.concurrent.atomic.AtomicMarkableReference;

public class LockFreeSetImpl<T extends Comparable<T>> implements LockFreeSet<T> {

    private LockFreeOrderedList<T> list;

    public LockFreeSetImpl() {
        list = new LockFreeOrderedList<>();
    }

    @Override
    public boolean add(T value) {
        return list.add(value);
    }

    @Override
    public boolean remove(T value) {
        return list.remove(value);
    }

    @Override
    public boolean contains(T value) {
        return list.contains(value);
    }

    @Override
    public boolean isEmpty() {
        return list.isEmpty();
    }
}

class LockFreeOrderedList<T extends Comparable<T>> {
    private ListNode head;

    public LockFreeOrderedList() {
        head = new ListNode(null, new AtomicMarkableReference<ListNode>(null, false));
    }

    public boolean add(T value) {
        while (true) {
            // find insert position
            NodesPair p = find(value);

            if (p.found != null && p.found.value.compareTo(value) == 0) {
                return false;
            } else {
                // create new node
                ListNode newNode = new ListNode(value, new AtomicMarkableReference<ListNode>(p.found, false));
                // insert with CAS
                if (p.prev.nextRef.compareAndSet(p.found, newNode, false, false)) {
                    return true;
                }
            }
        }
    }

    public boolean remove(T value) {
        while (true) {
            // find node
            NodesPair p = find(value);

            if (p.found == null || p.found.value.compareTo(value) != 0) {
                return false;
            } else {
                ListNode next = p.found.nextRef.getReference();
                // logical deletion - mark item found
                if (!p.found.nextRef.attemptMark(next, true)) {
                    continue;
                }
                // physical deletion - remove item found from list
                p.prev.nextRef.compareAndSet(p.found, next, false, false);
                return true;
            }
        }
    }

    public boolean contains(T value) {
        ListNode cur = head.nextRef.getReference();
        while (cur != null && cur.value.compareTo(value) < 0) {
            cur = cur.nextRef.getReference();
        }
        return cur != null && cur.value.compareTo(value) == 0 && !cur.nextRef.isMarked();
    }

    public boolean isEmpty() {
        return head.nextRef.getReference() == null;
    }

    public NodesPair find (T value) {
        loop:
        while (true) {
            ListNode prev, cur, next;
            prev = head;
            cur = prev.nextRef.getReference();

            while (cur != null) {
                AtomicMarkableReference<ListNode> nextRef = cur.nextRef;
                next = nextRef.getReference();

                if (nextRef.isMarked()) {
                    // physical deletion from list (change prev.next)
                    if (!prev.nextRef.compareAndSet(cur, next, false, false))
                        continue loop;
                    cur = next;
                } else {
                    if (cur.value.compareTo(value) >= 0) {
                        return new NodesPair(prev, cur);
                    }
                    prev = cur;
                    cur = next;
                }
            }
            return new NodesPair(prev, null);
        }
    }

    private class ListNode {
        T value;
        AtomicMarkableReference<ListNode> nextRef;

        public ListNode(T v, AtomicMarkableReference<ListNode> nr) {
            value = v;
            nextRef = nr;
        }
    }

    private class NodesPair {
        ListNode prev, found;

        public NodesPair(ListNode p, ListNode f) {
            prev = p;
            found = f;
        }
    }
}
