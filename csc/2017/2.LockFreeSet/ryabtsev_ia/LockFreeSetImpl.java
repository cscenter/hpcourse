import java.util.concurrent.atomic.AtomicMarkableReference;

/**
 * Created by Igor_Ryabtsev on 4/15/2017.
 */

public class LockFreeSetImpl<T extends Comparable<T>> implements LockFreeSet<T> {

    Node head;

    private class Node {
        T value;
        AtomicMarkableReference<Node> next;

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
            boolean expectedMarkCurr = curr != null && pred.next.isMarked();
            Node node = new Node(value, new AtomicMarkableReference<>(curr, expectedMarkCurr));
            if (pred.next.compareAndSet(curr, node, expectedMarkCurr, false)); {
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
                boolean currMarked = pred.next.isMarked();
                boolean isSuccMarked = succ != null && curr.next.isMarked();
                if (currMarked) {
                    if (!pred.next.compareAndSet(curr, succ, true, isSuccMarked)) {
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
        retry:
        while (true) {
            Pair pair = find(value);
            Node pred = pair.pred;
            Node curr = pair.curr;
            if (curr != null && curr.value.compareTo(value) != 0) {
                return false;
            }
            Node succ = curr.next.getReference();
            boolean isMarkedSucc = succ != null && curr.next.isMarked();
            if (!pred.next.attemptMark(curr, true)) {
                continue retry;
            }
            pred.next.compareAndSet(curr, succ, true, isMarkedSucc);
            return true;
        }
    }

    @Override
    public boolean contains(T value) {
        boolean[] marked = {false};
        Node node = head.next.getReference();
        while (node != null && node.value.compareTo(value) < 0) {
            node = node.next.get(marked);
        }
        if (node != null && node.value.compareTo(value) == 0 && !marked[0]) {
            return true;
        }
        return false;
    }

    @Override
    public boolean isEmpty() {
        boolean[] marked = {true};
        Node node = head;
        while (node != null && marked[0]) {
            node = node.next.get(marked);
        }
        return node == null;
    }
}
