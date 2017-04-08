import javafx.util.Pair;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicMarkableReference;

/**
 * Created by Sophia Titova on 06.04.17.
 */
public class LockFreeList<T extends Comparable<T>> implements LockFreeSet<T> {
    Node emptyNode = new Node();
    private AtomicMarkableReference<Node> head = new AtomicMarkableReference<>(emptyNode, false);
    private AtomicInteger size = new AtomicInteger(0);
    
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
        Node next;
        Pair<Node, Pair<Node, Node>> p;
        
        while (true) {
            
            p = find(value);
            pred = p.getKey();
            curr = p.getValue().getKey();
            next = p.getValue().getValue();
            
            if (curr != null) {
                return false;
            }
            
            Node node = new Node(value);
            node.setNext(new AtomicMarkableReference<>(next, false));
            
            if (size.get() == 0) {
                head.compareAndSet(emptyNode, node, false, false);
                size.compareAndSet(0, 1);
                return true;
            }
            
            if (pred.getNext().compareAndSet(next, node, false, false)) {   // а другие сслки перевесить? ходим в одну сторону телько видимо
                size.incrementAndGet();
                return true;
            }
        }
    }
    
    @Override
    public boolean remove(T value) {
        
        Node pred;
        Node curr;
        Node next;
        Pair<Node, Pair<Node, Node>> p;
        
        while (true) {
            
            p = find(value);
            pred = p.getKey();
            curr = p.getValue().getKey();
            next = p.getValue().getValue();
            
            if (size.get() == 1) {
                if (pred.getValue() == value) {
                    size.compareAndSet(1, 0);
                    return true;
                }
            }
            if (curr == null) {
                return false;
            }
            
            if (!curr.getNext().attemptMark(next, true)) {
                continue;
            }
            
            pred.getNext().compareAndSet(curr, next, false, false);
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
        Node pred;
        Node curr;
        Node next;
        boolean[] pMark = new boolean[1];
        boolean[] cMark = new boolean[1];
        int key = value.hashCode();
    again:
        while (true) {
            
            if (size.get() == 0) {
                return new Pair<>(null, new Pair<>(null, null));
            }
            pred = head.getReference();
            curr = pred.getNext().get(pMark);
            while (true) {
                if (curr == null) {
                    return new Pair<>(pred, new Pair<>(null, null));
                }
                next = curr.getNext().get(cMark);
                int ckey = curr.hashCode();
                if (pred.getNext().isMarked()) {
                    continue again;     // changed
                }
                if (!cMark[0]) {
                    if (curr.getValue().equals(value)) {
                        return new Pair<>(pred, new Pair<>(curr, next));
                    } else if (ckey <= key) {
                        pred = curr;
                    } else {
                        return new Pair<>(pred, new Pair<>(null, next));
                    }
                } else {
                    if (!pred.getNext().compareAndSet(curr, next, false, false)) {
                        continue again;
                    }
                }
            }
        }
    }
    
    
    private class Node {
        
        private AtomicMarkableReference<Node> next;
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
    }
}
