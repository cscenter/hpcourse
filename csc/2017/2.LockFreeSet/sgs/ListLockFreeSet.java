import java.util.concurrent.atomic.AtomicMarkableReference;

public class ListLockFreeSet<T extends Comparable<T>> implements LockFreeSet<T> {
    // Nodes of the list
    private  class Node {
        T value;
        AtomicMarkableReference<Node> reference;

        Node(T in_value, AtomicMarkableReference<Node> in_ref) {
            value = in_value;
            reference  = in_ref;
        }
    }
    // The class for finding proper place in the list
    private class Finding {
        Node prev, curr;
        Finding(T value) {
            retry: while (true) {
                prev = head;
                curr = head.reference.getReference();
                Node succ;
                while (curr != null) {
                    AtomicMarkableReference<Node> ref = curr.reference;
                    succ = ref.getReference();
                    if (ref.isMarked()) { // try to delete physically
                        if (!prev.reference.compareAndSet(curr, succ, false, false)) {
                            continue retry;
                        }
                        curr = succ;
                    } else {
                        if (curr.value.compareTo(value) >= 0) return;
                        prev = curr;
                        curr = succ;
                    }
                }
                return;
            }
        }
    }

    private Node head;

    public ListLockFreeSet() {
        head = new Node(null, new AtomicMarkableReference<>(null, false));
    }

    @Override
    public boolean add(T value) {
        while (true) {
            Finding pair = new Finding(value);
            Node curr = pair.curr;
            if (curr != null && curr.value.compareTo(value) == 0) {
                return false;
            } else {
                Node newNode = new Node(value, new AtomicMarkableReference<>(curr, false));
                if (pair.prev.reference.compareAndSet(curr, newNode, false, false)) {
                    return true;
                }
            }
        }
    }

    @Override
    public boolean remove(T value) {
        while (true) {
            Finding pair = new Finding(value);
            Node curr = pair.curr;
            if (curr == null || curr.value.compareTo(value) != 0) {
                return false;
            } else {
                Node succ = curr.reference.getReference();
                if (!curr.reference.attemptMark(succ, true)) {
                    continue;
                }
                pair.prev.reference.compareAndSet(curr, succ, false, false);
                return true;
            }
        }
    }

    @Override
    public boolean contains(T value) {
        boolean[] marked = {false};
        Node node = head.reference.getReference();
        while (node != null && node.value.compareTo(value) < 0) {
            node = node.reference.get(marked);
        }
        return (node != null && node.value.compareTo(value) == 0 && !marked[0]);
    }
    
    @Override
    public boolean isEmpty() {
        boolean[] marked = {true};
        Node node = head;
        // move through deleted elements
        while (node != null && marked[0]) {
            node = node.reference.get(marked);
        }
        // all are deleted
        if (node == null ) return true;
        // somebody is alive
        return false;
    }
}
