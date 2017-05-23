import java.util.concurrent.atomic.AtomicMarkableReference;

/**
 * Created by anatoliy on 4/14/17.
 */
public class LockFreeSetImpl<T extends Comparable<T>> implements LockFreeSet<T> {
    private final Node head;

    LockFreeSetImpl() {
        head = new Node();
    }

    @Override
    public boolean add(T value) {
        while (true) {
            Node pred = findPredecessor(value);
            Node next = pred.next.getReference();

            if (next != null && next.value.compareTo(value) == 0) {
                return false;
            } else {
                Node newNode = new Node(value);
                newNode.next.set(next, false);
                if (pred.next.compareAndSet(next, newNode, false, false)) {
                    return true;
                }
            }
        }
    }

    @Override
    public boolean remove(T value) {
        while (true) {
            Node pred = findPredecessor(value);
            Node found = pred.next.getReference();

            if (found == null || found.value.compareTo(value) != 0) {
                return false;
            }

            Node next = found.next.getReference();
            if (found.next.attemptMark(next, true)) {
                pred.next.compareAndSet(found, next, false, false);
                return true;
            }

        }
    }

    private Node findPredecessor(T value) {
        Node pred = head;
        Node curr = head.next.getReference();

        tryAgain: while (curr != null) {
            Node succ = curr.next.getReference();

            if (curr.next.isMarked()) {
                if (!pred.next.compareAndSet(curr, succ, false, false)) {
                    pred = head;
                    curr = head.next.getReference();
                    continue tryAgain;
                }
            } else {
                if (curr.value.compareTo(value) >= 0) {
                    return pred;
                }
                pred = curr;
            }
            curr = succ;
        }

        return pred;
    }

    @Override
    public boolean contains(T value) {
        Node cur = head.next.getReference();

        while (cur != null && cur.value.compareTo(value) < 0) {
            cur = cur.next.getReference();
        }

        return cur != null && !cur.next.isMarked() && cur.value.compareTo(value) == 0;
    }

    @Override
    public boolean isEmpty() {
        return head.next.getReference() == null;
    }

    private class Node {
        final T value;
        AtomicMarkableReference<Node> next;

        Node() {
            value = null;
            next = new AtomicMarkableReference<>(null, false);
        }

        Node(T value) {
            this.value = value;
            next = new AtomicMarkableReference<>(null, false);
        }

    }
}

