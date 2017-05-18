import java.util.concurrent.atomic.AtomicMarkableReference;

/**
 * Created by artemypestretsov on 4/16/17.
 */

public class LockFreeSetImpl<T extends Comparable<T>> implements LockFreeSet<T> {

    // head doesn't hold any value
    private Node head;

    public LockFreeSetImpl() {
        head = new Node(null, new AtomicMarkableReference<>(null, false));
    }

    @Override
    public boolean add(T value) {
        while (true) {
            PredCurrPair pair = find(value);
            Node pred = pair.pred;
            Node curr = pair.curr;

            if (curr != null && curr.key.compareTo(value) == 0) {
                return false;
            }

            Node node = new Node(value, new AtomicMarkableReference<>(curr, false));
            if (pred.next.compareAndSet(curr, node, false, false)) {
                return true;
            }
        }
    }

    @Override
    public boolean remove(T value) {
        while (true) {
            PredCurrPair pair = find(value);
            Node pred = pair.pred;
            Node curr = pair.curr;

            if (curr == null || curr.key.compareTo(value) != 0) {
                return false;
            }

            Node succ = curr.next.getReference();
            if (curr.next.attemptMark(succ, true)) {
                pred.next.compareAndSet(curr, succ, false, false);
                return true;
            }
        }
    }

    @Override
    public boolean contains(T value) {
        Node curr = head.next.getReference();

        while (curr != null && curr.key.compareTo(value) < 0) {
            curr = curr.next.getReference();
        }

        return curr != null && !curr.next.isMarked() && curr.key.compareTo(value) == 0;
    }

    // discussed in Slack
    @Override
    public boolean isEmpty() {
        return head.next.getReference() == null;
    }

    private PredCurrPair find(T value) {
        Node pred = head;
        Node curr = head.next.getReference();

        while (curr != null) {
            Node succ = curr.next.getReference();

            if (curr.next.isMarked()) {
                if (!pred.next.compareAndSet(curr, succ, false, false)) {
                    return find(value);
                }
            } else {
                if (curr.key.compareTo(value) >= 0) {
                    return new PredCurrPair(pred, curr);
                }
                pred = curr;
            }
            curr = succ;
        }

        return new PredCurrPair(pred, null);
    }

    private class Node {
        AtomicMarkableReference<Node> next;
        T key;

        Node(T key, AtomicMarkableReference<Node> next) {
            this.key = key;
            this.next = next;
        }
    }

    private class PredCurrPair {
        Node pred;
        Node curr;

        PredCurrPair(Node pred, Node curr) {
            this.pred = pred;
            this.curr = curr;
        }
    }
}
