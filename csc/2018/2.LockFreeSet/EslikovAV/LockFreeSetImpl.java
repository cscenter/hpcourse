import java.util.concurrent.atomic.AtomicMarkableReference;

public class LockFreeSetImpl<T extends Comparable<T>> implements LockFreeSet<T> {
    
    private final Node head;
    
    public LockFreeSetImpl() {
        this.head = new Node();
    }
    
    @Override
    public boolean add(T value) {
        while (true) {
            Window window = find(value);
            if (window.current != null && window.current.value != null && window.current.value.compareTo(value) == 0) {
                return false;
            } else {
                Node node = new Node(value, window.current);
                if (window.previous.next.compareAndSet(window.current, node, false, false)) {
                    return true;
                }
            }
        }
    }
    
    @Override
    public boolean remove(T value) {
        while (true) {
            Window window = find(value);
            if (window.current == null || window.current.value.compareTo(value) != 0) {
                return false;
            } else {
                Node successor = window.current.next.getReference();
                if (!window.current.next.attemptMark(successor, true)) {
                    continue;
                } else {
                    window.previous.next.compareAndSet(window.current, successor, false, false);
                    return true;
                }
            }
        }
    }
    
    @Override
    public boolean contains(T value) {
        Node current = head.next.getReference();
        
        while (current != null
               && current.value.compareTo(value) < 0) {
            current = current.next.getReference();
        }
        
        return current != null
        && !current.next.isMarked()
        && current.value.compareTo(value) == 0;
    }
    
    @Override
    public boolean isEmpty() {
        return head.next.getReference() == null;
    }
    
    private Window find(T value) {
        while (true) {
            Node previous = head;
            Node current = previous.next.getReference();
            Node successor;
            
            while (true) {
                if (current == null) {
                    return new Window(previous, null);
                }
                successor = current.next.getReference();
                if (current.next.isMarked()) {
                    if (!previous.next.compareAndSet(current, successor, false, false)) {
                        break;
                    }
                    current = successor;
                    
                } else {
                    if (current.value.compareTo(value) > 0) {
                        return new Window(previous, current);
                    }
                    previous = current;
                    current = successor;
                }
            }
        }
    }
    
    private final class Window {
        private final Node previous;
        private final Node current;
        
        Window(Node previous, Node current) {
            this.previous = previous;
            this.current = current;
            
        }
    }
    
    private final class Node {
        private final T value;
        private final AtomicMarkableReference<Node> next;
        
        Node() {
            this.value = null;
            this.next = new AtomicMarkableReference<>(null, false);
        }
        
        Node(T value, Node next) {
            this.value = value;
            this.next = new AtomicMarkableReference<>(next, false);
        }
    }
}
