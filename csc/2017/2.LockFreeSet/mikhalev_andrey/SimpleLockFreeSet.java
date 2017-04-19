/**
 * Created by montura on 10.04.17.
 */
import java.util.concurrent.atomic.AtomicMarkableReference;

public class SimpleLockFreeSet<T extends Comparable<T>> implements LockFreeSet<T> {
    private class Node {
        private T item;
        AtomicMarkableReference<Node> next; // reference

        Node() {
            item = null;
        }

        // constructor for add(T value)
        Node(T value) {
            item = value;
        }

        T get() {
            return item;
        }
    }

    // Lock-free List: |head| -> ... -> |tail|
    private Node head = new Node(); // head of list
    private Node tail = new Node(); // tail of list

    // Constructor
    public SimpleLockFreeSet() {
        head.next = new AtomicMarkableReference<>(tail, false);
        tail.next = new AtomicMarkableReference<>(tail, false);
    }

    // struct for find pair: |parent|->|child|
    private class NodePair {
        private Node parent, child;
        NodePair(Node my_prev, Node my_curr) {
            parent = my_prev;
            child = my_curr;
        }
    }

    // lock-free
    private NodePair find(T key) {
        Node parent = null, current = null, child = null;
        boolean marked_delete[] = {false};
        boolean hasDeleted;
        try {
            traverse: // label
            while (true) {
                parent = head;
                current = parent.next.getReference();
                while (true) {
                    // get reference to child and flag(should we marked as deleted or not)?
                    child = current.next.get(marked_delete);
                    while (marked_delete[0]) { // if we should be deleted, try physically delete
                        // IF (|parent.next| -> |child|) AND (parent.marked = false)
                        // THEN (|parent.next| -> |child|) AND (child.marked = false)
                        hasDeleted = parent.next.compareAndSet(current, child, false, false);
                        if (!hasDeleted) { // can't physically delete
                            continue traverse; // traverse list again
                        }
                        current = child; // now child = child.next
                        // get reference to child and flag(should we marked as deleted or not)?
                        child = current.next.get(marked_delete);
                    }
                    // if current != tail
                    if (current.item != null) {
                        // if we shouldn't be deleted and child.key >= finding_key
                        if (current.item.compareTo(key) >= 0) {
                            return new NodePair(parent, current);
                        }
                    } else {
                        return new NodePair(parent, current);
                    }
                    parent = current;
                    current = child;
                }
            }
        } catch (NullPointerException e) {
            e.printStackTrace();
            return new NodePair(parent, current);
        }
    }

    // lock-free
    @Override
    public boolean add(T value) {
        while (true) {
            NodePair nodePair = find(value); // find place for insert
            Node parent = nodePair.parent, child = nodePair.child;
            // if child != tail, then compare ITEM with VALUE
            if (child.item != null) {
                if (child.get().compareTo(value) == 0) { // check for unique
                    return false;
                }
            }
            // else add new node
            Node new_node = new Node(value);
            new_node.next = new AtomicMarkableReference<>(child, false);
            // IF (|parent.next| -> |child|) AND (parent.marked = false)
            // THEN (|parent.next| -> |new_node|) AND (new_node.marked = false)
            if (parent.next.compareAndSet(child, new_node, false, false)) {
                return true;
            }

        }
    }

    // lock-free
    @Override
    public boolean remove(T value) {
        boolean will_be_deleted = false;
        while (true) {
            NodePair nodePair = find(value); // find node for delete
            Node parent = nodePair.parent, current = nodePair.child;
            // if child != tail, then compare ITEM with VALUE
            if (current.item != null) {
                if (current.get().compareTo(value) != 0) { // matching value's key and child's key
                    return false;
                }
            }
            // else mark node as logically removed
            Node child = current.next.getReference(); // get reference to child
            // IF (|child.next| -> |child|) THEN (child.marked = true) ~ as logically deleted
            will_be_deleted = current.next.attemptMark(child, true);
            if (!will_be_deleted) { // if can't marked child element as deleted
                continue; // try remove again (traverse list again)
            }
            // IF (|parent.next| -> |child|) AND (parent.marked = false)
            // THEN (|parent.next| -> |child|) AND (child.marked = false)
            parent.next.compareAndSet(current, child, false, false); // try physically delete
            return true;
        }
    }

    // wait-free
    @Override
    public boolean contains(T value) {
        boolean[] marked_deleted = {false};
        Node current = head.next.getReference();
        // (current.item != null) : current != tail
        while ((current.item != null) && current.get().compareTo(value) < 0) { // traverse list
                current = current.next.getReference();
                current.next.get(marked_deleted); // check for logical delete
        }
        return ((current.get().compareTo(value) == 0) && !marked_deleted[0]);
    }

    // wait-free
    // совсем неоднозначно в случае, если один поток прошел и логически удалил элементы,
    // а второй запросил empty. Множество будет не пустым физически, хотя пусто логически.
    @Override
    public boolean isEmpty() {
        return head.next.getReference() == tail;
    }
}
