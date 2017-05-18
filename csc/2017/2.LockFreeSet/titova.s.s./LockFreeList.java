import javafx.util.Pair;
import java.util.concurrent.atomic.AtomicMarkableReference;

/**
 * @author Sophia Titova.
 */
public class LockFreeList<T extends Comparable<T>> implements LockFreeSet<T> {
    private Node HEAD;
    private Node TAIL;


    public LockFreeList() {
        this.HEAD = new Node(null);
        this.TAIL = new Node(null);
        this.HEAD.next.set(this.TAIL, false);
    }

    public static void main(String[] args) {
        LockFreeList<Integer> l = new LockFreeList<>();
        l.add(1);
        l.remove(2);
        System.out.println(l.contains(1));
        System.out.println(l.isEmpty());
    }

//    private boolean checkNotDeleted(Node first, Node second) {
//        return !first.getNext().isMarked() && !second.getNext().isMarked() && first.getNext().getReference() == second;
//    }

    @Override
    public boolean add(T value) {

        Node prev;
        Node curr;
        Pair<Node, Pair<Node, Node>> p;
        while (true) {
            p = find(value);
            prev = p.getKey();
            curr = p.getValue().getKey();

            if (curr.getValue() != null && curr.getValue().compareTo(value) == 0) {
                return false;
            }
            Node node = new Node(value);
            node.setNext(new AtomicMarkableReference<>(curr, false));
            if (prev.getNext().compareAndSet(curr, node, false, false)) {
                return true;
            }

        }
    }

    @Override
    public boolean remove(T value) {
        Node prev;
        Node curr;
        Node next;
        Pair<Node, Pair<Node, Node>> p;
        while (true) {
            p = find(value);
            prev = p.getKey();
            curr = p.getValue().getKey();
            next = p.getValue().getValue();

            if (curr.getValue() == null || curr.getValue().compareTo(value) != 0) {
                return false;
            }
//            curr.setDeleted(true);
            if (!curr.getNext().attemptMark(next, true)) {
                continue;
            }
            prev.getNext().compareAndSet(curr, next, false, false);
            return true;
            
        }
    }


    @Override
    public boolean contains(T value) {
        Node curr = HEAD.getNext().getReference();
        while (curr.getValue() != null && curr.getValue().compareTo(value) != 0) {
            curr = curr.getNext().getReference();
        }
        return curr.getValue() != null && !curr.getNext().isMarked() && curr.getValue().compareTo(value) == 0;
    }

    @Override
    public boolean isEmpty() {
        return this.HEAD.getNext().getReference() == this.TAIL;
    }

    private Pair<Node, Pair<Node, Node>> find(T value) {
        Node pred;
        Node curr;
        Node next;
        boolean[] cMark = new boolean[1];
        cMark[0] = false;
        again:
        while (true) {
            pred = HEAD;
            curr = HEAD.next.getReference();
            while (true) {
                next = curr.getNext().get(cMark);
                if (pred.getNext().isMarked()) {
                    continue again;
                }
                while (cMark[0]) {
                    if (!pred.getNext().compareAndSet(curr, next, false, false)) {
                        continue again;
                    }
                    curr = pred.getNext().getReference();
                    next = curr.getNext().get(cMark);
                }
                if (curr.getValue() == null || curr.getValue().compareTo(value) == 0) {
                    return new Pair<>(pred, new Pair<>(curr, next));
                }
                pred = curr;
                curr = next;
            }
        }
    }


    private class Node {

//        private AtomicBoolean deleted = new AtomicBoolean();
        private AtomicMarkableReference<Node> next;
        private T value;

        Node(T value) {
//            deleted.set(false);
            this.value = value;
            next = new AtomicMarkableReference<Node>(null, false);
        }

        T getValue() {
            return value;
        }

//        boolean getDeleted() {
//            return deleted.get();
//        }
//
//        void setDeleted(boolean b) {
//            deleted.set(b);
//        }

        AtomicMarkableReference<Node> getNext() {
            return next;
        }

        void setNext(AtomicMarkableReference<Node> next) {
            this.next = next;
        }
    }
}
