import java.util.concurrent.atomic.AtomicMarkableReference;

public class LockFreeSetImpl<T extends Comparable<T>> implements LockFreeSet<T> {
    private Node head;

    public LockFreeSetImpl() {
        head = new Node(null);
        Node tail = new Node(null);
        head.next.set(tail, false);
    }


    @Override
    public boolean add(T value) {
        if (value == null) {
            // Otherwise it will raise an exception later on `.compareTo`
            throw new IllegalArgumentException("`null` reserved for internal use (for head and tail)");
        }

        while (true) {
            Window window = find(value);
            Node prev = window.prev;
            Node curr = window.curr;
            // This value already presented in set
            if (curr.value != null && curr.value.compareTo(value) == 0) {
                return false;
            }
            Node node = new Node(value);
            node.next.set(curr, false);
            if (prev.next.compareAndSet(curr, node, false, false)) {
                return true;
            }
        }
    }

    @Override
    public boolean remove(T value) {
        // Otherwise it will raise an exception later on `.compareTo`
        if (value == null) {
            throw new IllegalArgumentException("Did you try to delete head or tail?");
        }
        while (true) {
            Window window = find(value);
            Node prev = window.prev;
            Node curr = window.curr;

            if (curr.value == null || curr.value.compareTo(value) != 0) {
                return false;
            }

            Node next = curr.next.getReference();
            // FIXME: Why we mark to delete next node instead of the current?
            if (!curr.next.compareAndSet(next, next, false, true)) {
                continue;
            }
            prev.next.compareAndSet(curr, next, false, false);
            return true;
        }
    }

    private class Window {
        Node prev;
        Node curr;

        Window(Node prev, Node curr) {
            this.prev = prev;
            this.curr = curr;
        }
    }

    private Window find(T key) {
        Node prev, curr, next;
        boolean to_delete[] = { false };
        boolean hasBeenDeleted;
        retry: // LABEL
        while (true) {
            prev = head;
            curr = prev.next.getReference();
            while (true) {
                next = curr.next.get(to_delete);
                // FIXME: The same question as above (see `remove` method)
                while (to_delete[0]) { // marked to delete
                    hasBeenDeleted = prev.next.compareAndSet(curr, next, false, false);
                    if (!hasBeenDeleted) {
                        continue retry;
                    }
                    curr = next;
                    next = curr.next.get(to_delete);
                }
                if (curr.value == null || curr.value.compareTo(key) >= 0) {
                    return new Window(prev, curr);
                }
                prev = curr;
                curr = next;
            }
        }
    }

    @Override
    public boolean contains(T value) {
        boolean[] to_delete = { false };
        Node current = head.next.getReference();
        while (current.value != null && current.value.compareTo(value) < 0) {
            current = current.next.get(to_delete);
        }
        return (current.value != null &&
                current.value.compareTo(value) == 0 &&
                !to_delete[0]);
    }

    @Override
    public boolean isEmpty() {
        return head.next.getReference().value == null;
    }

    private class Node {
        T value;
        AtomicMarkableReference<Node> next;

        Node(T value) {
            this.value = value;
            this.next = new AtomicMarkableReference<Node>(null, false);
        }
    }

    public static void main(String[] args) {
        LockFreeSetImpl<Integer> s = new LockFreeSetImpl<>();
        s.add(3);
        s.add(4);
        s.remove(4);
//        s.add(5);
        System.out.println(s.contains(4));
        System.out.println(s.contains(3));
    }
}
