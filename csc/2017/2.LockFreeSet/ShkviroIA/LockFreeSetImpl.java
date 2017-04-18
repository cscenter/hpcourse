/**
 * Created by Fenix on 17.04.2017.
 */

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicMarkableReference;

public class LockFreeSetImpl<T extends Comparable<T>> implements LockFreeSet<T> {

    private class Node {
        Node(final T value, final AtomicMarkableReference<Node> next) {
            this.value = value;
            this.next = next;
        }

        T value;
        AtomicMarkableReference<Node> next;
    }

    LockFreeSetImpl() {
        this.head = new AtomicMarkableReference<Node>(null, false);
        this.size = new AtomicInteger(0);
    }

    private AtomicMarkableReference<Node> head;
    private AtomicInteger size;

    @Override
    public boolean add(T value) {
        while (true) {
            AtomicMarkableReference<Node> curr = head, pred = new AtomicMarkableReference<Node>(null, false);
            Node last_not_deleted = null, last_not_deleted_next = null;
            boolean tail_mark = false;
            while (curr.getReference() != null) {
                if (!curr.isMarked()) {
                    if (curr.getReference().value.compareTo(value) == 0) {
                        return false;
                    } else {
                        last_not_deleted = curr.getReference();
                        last_not_deleted_next = curr.getReference().next.getReference();
                        tail_mark = curr.isMarked();
                    }
                }
                pred = curr;
                curr = curr.getReference().next;
            }

            Node new_node = new Node(value,  new AtomicMarkableReference<Node>(null, false));
            if (last_not_deleted == null) {
                if (head.compareAndSet(null, new_node, tail_mark, false)) {
                    size.incrementAndGet();
                    return true;
                }
            }

            if (last_not_deleted.next.compareAndSet(last_not_deleted_next, new_node, tail_mark, false)) {
                size.incrementAndGet();
                return true;
            }
        }
    }

    public boolean remove(T value) {
        while (true) {
            AtomicMarkableReference<Node> curr = head, pred = new AtomicMarkableReference<Node>(null, false);
            while (curr.getReference() != null && curr.getReference().value.compareTo(value) != 0) {
                pred = curr;
                curr = curr.getReference().next;
            }

            if (curr.getReference() == null) {
                return false;
            }

            Node curr_node = curr.getReference();
            if (pred.getReference() == null) {
                if (head.compareAndSet(curr_node, null, false, true)) {
                    size.decrementAndGet();
                    return true;
                }
            }

            Node pred_node = pred.getReference();
            Node next_node = curr_node.next.getReference();
            if (pred_node.next.compareAndSet(curr_node, next_node, false, true)) {
                size.decrementAndGet();
                return true;
            }
        }
    }

    public boolean contains(T value) {
        while (true) {
            AtomicMarkableReference<Node> curr;;
            curr = head;
            while (curr.getReference() != null) {
                if (curr.getReference().value == value && !curr.isMarked()) {
                    return true;
                }
                curr = curr.getReference().next;
            }

            return false;
        }
    }

    public boolean isEmpty() {
        return (size.get() == 0);
    }
}