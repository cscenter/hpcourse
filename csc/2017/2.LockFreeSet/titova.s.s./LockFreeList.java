import javafx.util.Pair;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicMarkableReference;

/**
 * Created by Sophia Titova on 06.04.17.
 */
public class LockFreeList<T extends Comparable<T>> implements LockFreeSet<T> {
    
    private AtomicMarkableReference<Node> head;
    private AtomicInteger size = new AtomicInteger(0);
    
    public LockFreeList() {
    }
    
    public static void main(String[] args) {
        LockFreeList<Integer> integerLockFreeList = new LockFreeList<>();
        integerLockFreeList.add(1);
        integerLockFreeList.add(2);
        integerLockFreeList.add(2);
        integerLockFreeList.remove(1);
        System.out.println(integerLockFreeList.contains(1));
        
        integerLockFreeList.remove(2);
        System.out.println(integerLockFreeList.contains(2));
    }
    
    @Override
    public boolean add(T value) {
        
        Node pred;
        Node curr;
        Node succ;
        Pair<Node, Pair<Node, Node>> p;
        
        while (true) {
            
            p = find(value);
            pred = p.getKey();
            curr = p.getValue().getKey();
            succ = p.getValue().getValue();
            
            if (curr != null) {
                return false;
            }
            
            Node node = new Node(value);
            node.setNext(new AtomicMarkableReference<>(succ, false));
            
            if (size.get() == 0) {
                head = new AtomicMarkableReference<Node>(node, false);
                size.incrementAndGet();
                return true;
            }
            
            if (pred.getNext().compareAndSet(succ, node, false, false)) {   // а другие сслки перевесить? ходим в одну сторону телько видимо
                size.incrementAndGet();
                return true;
            }
        }
    }
    
    @Override
    public boolean remove(T value) {
        
        Node pred;
        Node curr;
        Node succ;
        Pair<Node, Pair<Node, Node>> p;
        
        while (true) {
            
            p = find(value);
            pred = p.getKey();
            curr = p.getValue().getKey();
            succ = p.getValue().getValue();
            
            if (size.get() == 1) {
                if (pred.getValue() == value) {
                    size.compareAndSet(1, 0);
                    return true;
                }
            }
            if (curr == null) {
                return false;
            }
            
            if (!curr.getNext().attemptMark(succ, true)) {
                continue;
            }
            
            pred.getNext().compareAndSet(curr, succ, false, false);
            size.decrementAndGet();
            return true;
        }
    }
    
    
    @Override
    public boolean contains(T value) {
        while (true) {
            return find(value).getValue().getKey() != null;
        }
    }
    
    @Override
    public boolean isEmpty() {
        return size.get() == 0;
    }
    
    private Pair<Node, Pair<Node, Node>> find(Comparable value) { // TODO
        //Pair<Node, Pair<Node, Node>> result;
        Node pred;
        Node curr;
        Node succ;
        boolean[] pmark = new boolean[1];
        boolean[] cmark = new boolean[1];
        int key = value.hashCode();
    again:
        while (true) {
            
            if (size.get() == 0) {
                return new Pair<>(null, new Pair<>(null, null));
            }
            pred = head.getReference();
            curr = pred.getNext().get(pmark);
            while (true) {
                if (curr == null) {
                    return new Pair<>(pred, new Pair<>(null, null));
                }
                succ = curr.getNext().get(cmark);
                int ckey = curr.hashCode();
                if (pred.getNext().isMarked()) {
                    continue again;     // changed
                }
                if (!cmark[0]) {
                    if (curr.getValue().equals(value)) {
                        return new Pair<>(pred, new Pair<>(curr, succ));
                    } else if (ckey <= key) {
                        pred = curr;
                    } else {
                        return new Pair<>(pred, new Pair<>(null, succ));
                    }
                } else {
                    if (!pred.getNext().compareAndSet(curr, succ, false, false)) {
                        continue again;
                    }
                }
            }
        }
        //return new Pair<>(new Node(), new Pair<>(new Node(), new Node()));
    }
    
    
    private class Node {
        
        private AtomicMarkableReference<Node> next;
        private AtomicMarkableReference<Node> pred;
        private T value;
        
        Node() {
        }
        
        Node(T value) {
            this.value = value;
        }
        
        T getValue() {
            return value;
        }
        
        AtomicMarkableReference<Node> getNext() {
            return next;
        }
        
        void setNext(AtomicMarkableReference<Node> next) {
            this.next = next;
        }
        
        AtomicMarkableReference<Node> getPred() {
            return pred;
        }
    }
}
