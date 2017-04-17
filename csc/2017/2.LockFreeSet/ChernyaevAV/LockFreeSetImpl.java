import java.util.concurrent.atomic.AtomicMarkableReference;

//implementation based on lock free linked list from 'Art of multiprocessor programming'
//Can upgrade it to lock free hash set if needed
public class LockFreeSetImpl<T extends Comparable<T>> implements LockFreeSet<T> {
    private Node _head = new Node();

    @Override
    public boolean add(T value) {
        while (true) {
            Window window = search(value);
            Node previous = window.previous, current = window.current;
            if (current != null && current.value.compareTo(value) == 0) {
                return false;
            } else {
                Node node = new Node(value, current);
                if (previous.next.compareAndSet(current, node, false, false)) {
                    return true;
                }
            }
        }
    }

    @Override
    public boolean remove(T value) {
        while (true) {
            Window window = search(value);
            Node previous = window.previous, current = window.current;
            if (current == null || current.value.compareTo(value) != 0) {
                return false;
            } else {
                Node successor = current.next.getReference();
                boolean successfullyMarked = current.next.attemptMark(successor, true);
                if (!successfullyMarked)
                    continue;
                previous.next.compareAndSet(current, successor, false, false);
                return true;
            }
        }
    }

    @Override
    public boolean contains(T value) {  //this method should be wait-free, so didn't use search(T) (without removing logically deleted)
        boolean[] markHolder = {false};
        Node current = _head.next.get(markHolder);
        while (current != null && current.value.compareTo(value) < 0) {
            current = current.next.get(markHolder);
        }

        return current != null && current.value.compareTo(value) == 0 && !markHolder[0];
    }

    @Override
    public boolean isEmpty() {
        return _head.next.getReference() == null;
    }

    private Window search(T key) { //lock free search for key
        Node previous, current, successor;
        boolean[] markHolder = {false}; //markholder, use it to get two values from method 'get'
        retry:
        //sorry for copy-pasted label from 'Art of multiprocessor programming', but it looks cute here
        while (true) {
            previous = _head;
            current = previous.next.getReference();
            while (current != null) {
                successor = current.next.get(markHolder);
                while (markHolder[0]) { //skip logically deleted nodes
                    if (!previous.next.compareAndSet(current, successor, false, false)) continue retry;
                    current = successor;
                    successor = current.next.get(markHolder);
                }
                if (current.value.compareTo(key) >= 0)
                    return new Window(previous, current);
                previous = current;
                current = successor;
            }
            return new Window(previous, null);
        }
    }

    class Node {
        T value;
        AtomicMarkableReference<Node> next;

        Node(T value) {
            this.value = value;
            next = new AtomicMarkableReference<>(null, false);
        }

        Node() {
            next = new AtomicMarkableReference<>(null, false);
        }

        Node(T value, Node next) {
            this.value = value;
            this.next = new AtomicMarkableReference<>(next, false);
        }
    }

    class Window {
        Node previous, current;

        Window(Node previous, Node current) {
            this.previous = previous;
            this.current = current;
        }
    }
}
