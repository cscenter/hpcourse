import java.util.concurrent.atomic.AtomicMarkableReference;

/**
 * Created by Igor_Ryabtsev on 4/15/2017.
 */

public class LockFreeSetImpl<T extends Comparable<T>> implements LockFreeSet<T> {

    Node head;

    private class Node {
        T value;
        AtomicMarkableReference<Node> next; //save here the next node and the mark for current node

        public Node(T value, AtomicMarkableReference<Node> next) {
            this.value = value;
            this.next = next;
        }
    }

    private class Pair {
        Node pred;
        Node curr;

        public Pair(Node pred, Node curr) {
            this.pred = pred;
            this.curr = curr;
        }
    }

    LockFreeSetImpl() {
        //the head is always empty
        head = new Node(null, new AtomicMarkableReference<Node>(null, false));
    }

    @Override
    public boolean add(T value) {
        while (true) {
            Pair pair = find(value);
            Node pred = pair.pred;
            Node curr = pair.curr;
            if (curr != null && curr.value.equals(value)) {
                return false;
            }
            Node node = new Node(value, new AtomicMarkableReference<>(curr, false));
            if (pred.next.compareAndSet(curr, node, false, false)) {
                return true;
            }
        }
    }

    private Pair find(T value) {
        retry:
        while (true) {
            Node pred = head;
            Node curr = pred.next.getReference();
            Node succ;
            while (curr != null) {
                succ = curr.next.getReference();
                boolean currMarked = curr.next.isMarked();
                if (currMarked) {
                    if (!pred.next.compareAndSet(curr, succ, false, false)) {
                        continue retry;
                    }
                    curr = succ;
                } else {
                    if (curr.value.compareTo(value) >= 0) {
                        return new Pair(pred, curr);
                    }
                    pred = curr;
                    curr = succ;
                }
            }
            return new Pair(pred, curr);
        }
    }

    @Override
    public boolean remove(T value) {
        while (true) {
            Pair pair = find(value);
            Node pred = pair.pred;
            Node curr = pair.curr;
            if (curr == null || curr.value.compareTo(value) != 0) {
                return false;
            }
            Node succ = curr.next.getReference();
            if (!curr.next.attemptMark(succ, true)) {
                continue;
            }
            pred.next.compareAndSet(curr, succ, false, false);
            return true;
        }
    }

    @Override
    public boolean contains(T value) {
        Node node = head.next.getReference();
        while (node != null && node.value.compareTo(value) < 0) {
            node = node.next.getReference();
        }
        return node != null && node.value.compareTo(value) == 0 && !node.next.isMarked();
    }

    @Override
    public boolean isEmpty() {
        Node node = head.next.getReference();
        while (node != null && node.next.isMarked()) {
            node = node.next.getReference();
        }
        return node == null;
    }
}
