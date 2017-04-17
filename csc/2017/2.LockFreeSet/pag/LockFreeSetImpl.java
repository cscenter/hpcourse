import java.util.concurrent.atomic.AtomicMarkableReference;

import static java.util.Objects.requireNonNull;

public class LockFreeSetImpl<T extends Comparable<T>> implements LockFreeSet<T> {

    private final Node headNode;
    private final Node lastNode;


    public LockFreeSetImpl() {
        this.lastNode = new Node(null);
        this.headNode = new Node(null,new AtomicMarkableReference<Node>(lastNode,false));
    }

    @Override
    public boolean add(T value) {
        requireNonNull(value);
        while (true) {
            Result r = find(value);
            if (r.curr.key != null && r.curr.key.equals(value)) {
                return false;
            } else {
                Node newNode = new Node(value);
                newNode.next.set(r.curr, false);
                if (r.prev.next.compareAndSet(r.curr, newNode, false, false)) {
                    return true;
                } else {
                    continue;
                }
            }
        }
    }

    @Override
    public boolean remove(T value) {
        requireNonNull(value);
        while (true) {
            Result r = find(value);
            if (r.curr.key == null || !r.curr.key.equals(value)) {
                return false;
            } else {
                if (r.prev.next.attemptMark(r.curr, true)) {
                    return true;
                } else {
                    continue;
                }
            }
        }
    }

    @Override
    public boolean contains(T value) {
        requireNonNull(value);
        final Result r = find(value);
        return r.curr.key !=null && r.curr.key.equals(value);
    }

    @Override
    public boolean isEmpty() {
        boolean[] currMarked = {false};
        Node curr = headNode.next.get(currMarked);
        while (curr.key != null && currMarked[0]) {
            curr = curr.next.get(currMarked);
        }
        return curr.key == null;
    }


    private Result find(T value) {
        Node prev = headNode;
        Node curr = headNode.getNext();
        while (curr.key != null && curr.key.compareTo(value) < 0) {
            prev = curr;
            curr = prev.getNext();
        }
        return new Result(prev, curr);
    }

    private  class Result {
        final Node prev;
        final Node curr;

        Result(Node prev, Node cur) {
            this.prev = prev;
            this.curr = cur;
        }
    }

    private class Node {
        private final T key;
        private final AtomicMarkableReference<Node> next;

        Node(T k,AtomicMarkableReference<Node> next) {
            key = k;
            this.next = next;
        }

        Node(T k) {
            key = k;
            this.next = new AtomicMarkableReference<Node>(null, false);
        }

        public Node getNext() {
            boolean[] currMarked = {false};
            boolean[] succMarked = {false};
            Node curr = this.next.get(currMarked);
            while (currMarked[0]) {
                Node succ = curr.next.get(succMarked);
                this.next.compareAndSet(curr, succ, true, succMarked[0]);
                curr = this.next.get(currMarked);
            }
            return curr;
        }
    }
}
