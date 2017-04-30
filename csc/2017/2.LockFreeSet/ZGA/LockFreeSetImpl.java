// http://cs.ipm.ac.ir/asoc2016/Resources/Theartofmulticore.pdf
// https://docs.oracle.com/javase/7/docs/api/java/util/concurrent/atomic/AtomicMarkableReference.html
import java.util.concurrent.atomic.AtomicMarkableReference;

public class LockFreeSetImpl<T extends Comparable<T>> implements LockFreeSet<T> {
    private class Node<T extends Comparable<T>> {
        public Node(T value, Node<T> next) {
            this.value = value;
            this.next = new AtomicMarkableReference<>(next, false);
        }

        public T value;
        public AtomicMarkableReference<Node<T>> next;
    }

    private Node<T> head; //dummy nodes (head + tail)

    private class SlidingWindow<T extends Comparable<T>> {
        public SlidingWindow(Node<T> previous, Node<T> current) {
            this.previous = previous;
            this.current = current;
        }

        public Node<T> previous;
        public Node<T> current;
    }

    private SlidingWindow<T> find(T value) {
        retry: while (true) {
            Node<T> previous = head;
            Node<T> current = previous.next.getReference();
            while (true) {
                boolean[] mark = {false};
                Node<T> success = current.next.get(mark);
                while (mark[0]) {
                    if (!previous.next.compareAndSet(current, success, false, false)) {
                        continue retry;
                    } else {
                        current = success;
                        success = current.next.get(mark);
                    }
                }

                if (current.value == null || value.compareTo(current.value) <= 0) {
                    return new SlidingWindow<>(previous, current);
                } else {
                    previous = current;
                    current = success;
                }
            }
        }
    }

    public LockFreeSetImpl() {
        head = new Node<T>(null, new Node<T>(null, null));
    }

    public boolean add(T value){
        while (true) {
            SlidingWindow<T> previousCurrentPair = find(value);
            Node<T> previous = previousCurrentPair.previous;
            Node<T> current = previousCurrentPair.current;
            if (current.value == null || value.compareTo(current.value) != 0) {
                Node<T> node = new Node<T>(value, current);
                if (previous.next.compareAndSet(current, node, false, false)) {
                    return true;
                }
            } else {
                return false;
            }
        }
    }

    public boolean remove(T value){
        while (true) {
            SlidingWindow<T> previousCurrentPair = find(value);
            Node<T> previous = previousCurrentPair.previous;
            Node<T> current = previousCurrentPair.current;
            if (current.value != null && value.compareTo(current.value) == 0) {
                Node<T> success = current.next.getReference();
                if (!current.next.attemptMark(success, true)) {
                    continue;
                } else {
                    previous.next.compareAndSet(current, success, false, false);
                    return true;
                }
            } else {
                return false;
            }
        }
    }

    public boolean contains(T value){
        boolean[] marked = {false};
        Node<T> current = head.next.get(marked);
        while (current.value != null && current.value.compareTo(value) < 0) {
            current = current.next.get(marked);
        }

        return  current.value != null && current.value.compareTo(value) == 0 && !marked[0];
    }

    public boolean isEmpty(){
        Node<T> nextNode = head.next.getReference();
        return nextNode.value == null;
    }
}