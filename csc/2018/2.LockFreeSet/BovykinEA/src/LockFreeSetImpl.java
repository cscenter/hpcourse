
import java.util.concurrent.atomic.AtomicMarkableReference;
import java.lang.Comparable;

class MinusInfinity implements Comparable {
    @Override
    public int compareTo(Object o) {
        return -1;
    }
}

class PlusInfinity implements Comparable {
    @Override
    public int compareTo(Object o){
        return 1;
    }
}

public class LockFreeSetImpl implements LockFreeSet {

    Node head = new Node(new MinusInfinity(), new Node(new PlusInfinity()));

    @Override
    public boolean add(Comparable value) {
        while(true){
            Neighbors neighbors = find(head, value);
            Node pred = neighbors.pred;
            Node curr = neighbors.curr;

            if(curr.data.compareTo(value) == 0){
                return false;
            } else {
                Node node = new Node(value, curr);

                if(pred.next.compareAndSet(curr, node, false, false)){
                    return true;
                }
            }
        }
    }

    @Override
    public boolean remove(Comparable value) {
        while(true){
            Neighbors neighbors = find(head, value);

            Node pred = neighbors.pred;
            Node curr = neighbors.curr;

            if(curr.data.compareTo(value) != 0){
                return false;
            } else {
                Node succ = (Node)curr.next.getReference();

                if(!curr.next.compareAndSet(succ, succ, false, true)){
                    continue;
                }

                pred.next.compareAndSet(curr, succ, false, false);

                return true;
            }
        }
    }

    @Override
    public boolean contains(Comparable value) {
        boolean[] marked = {false};

        Node curr = head;

        while(curr.data.compareTo(value) < 0){
            curr = (Node)curr.next.getReference();
        }

        curr.next.get(marked);

        return curr.data.compareTo(value) == 0 && !marked[0];
    }

    @Override
    public boolean isEmpty() {
        return head.next.getReference() == null;
    }

    private Neighbors find(Node head, Comparable val){
        Node pred = null;
        Node curr = null;
        Node succ = null;

        boolean[] marked = {false};

        retry: while(true){
            pred = head;
            curr = (Node)pred.next.getReference();

            while(true){
                if(curr.next != null) {
                    succ = (Node) curr.next.get(marked);

                    while (marked[0]) {
                        if (!pred.next.compareAndSet(curr, succ, false, false)) {
                            continue retry;
                        }

                        curr = succ;
                        succ = (Node) curr.next.get(marked);
                    }
                }

                if(curr.data.compareTo(val) >= 0){
                    return new Neighbors(pred, curr);
                }

                pred = curr;
                curr = succ;
            }
        }
    }
}

class Node<T extends Comparable> {
    T data;
    AtomicMarkableReference<Node> next;

    Node(T data, Node next){
        this.data = data;
        this.next = new AtomicMarkableReference<>(next, false);
    }

    Node(T data){
        this.data = data;
        this.next = new AtomicMarkableReference<>(null, false);
    }

    boolean isLess(Node other){
        return data.compareTo(other.data) < 0;
    }
}

class Neighbors {
    public Node pred;
    public Node curr;

    Neighbors(Node pred, Node curr){
        this.pred = pred;
        this.curr = curr;
    }
}