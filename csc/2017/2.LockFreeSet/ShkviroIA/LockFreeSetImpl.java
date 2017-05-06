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

        final T value;
        AtomicMarkableReference<Node> next;
    }

    private class NodePair {
        NodePair(AtomicMarkableReference<Node> first, AtomicMarkableReference<Node> second) {
            this.first = first;
            this.second = second;
        }

        AtomicMarkableReference<Node> first;
        AtomicMarkableReference<Node> second;
    }

    public LockFreeSetImpl() {
        this.head = new AtomicMarkableReference<Node>(null, false);
        this.size = new AtomicInteger(0);
    }

    private AtomicMarkableReference<Node> head;
    private AtomicInteger size;

    NodePair find(T value) {
        AtomicMarkableReference<Node> curr = head, prev = new AtomicMarkableReference<Node>(null, false);
        AtomicMarkableReference<Node> last_not_deleted = null, last_not_deleted_next = null;
        while (curr.getReference() != null) {
            if (!curr.isMarked()) {
                if (curr.getReference().value.compareTo(value) == 0) {
                    return new NodePair(curr, prev);
                } else {
                    last_not_deleted = curr;
                    last_not_deleted_next = curr.getReference().next;
                }
            }
            prev = curr;
            curr = curr.getReference().next;
        }
        return new NodePair(last_not_deleted_next, last_not_deleted);
    }

    @Override
    public boolean add(T value) {
        while (true) {
            NodePair finded = find(value);
            AtomicMarkableReference<Node> last_not_deleted = finded.second, last_not_deleted_next = finded.first;
            if (last_not_deleted_next != null && last_not_deleted_next.getReference() != null && last_not_deleted_next.getReference().value.compareTo(value) == 0) {
                return false;
            }

            Node new_node = new Node(value,  new AtomicMarkableReference<Node>(null, false));
            if (last_not_deleted == null) {
                if (head.compareAndSet(null, new_node, false, false)) {
                    size.incrementAndGet();
                    return true;
                }
            } else {
                boolean tail_mark = last_not_deleted.isMarked();
                Node last_node = last_not_deleted.getReference();
                Node last_node_next = last_not_deleted_next.getReference();
                if (last_node.next.compareAndSet(last_node_next, new_node, tail_mark, false)) {
                    size.incrementAndGet();
                    return true;
                }
            }
        }
    }

    public boolean remove(T value) {
        while (true) {
            NodePair finded = find(value);
            AtomicMarkableReference<Node> prev = finded.second, curr = finded.first;
            Node prev_node = prev.getReference();
            Node curr_node = curr.getReference();
            if (curr_node.value.compareTo(value) != 0) {
                return false;
            }

            Node next_node = curr_node.next.getReference();
            if (prev_node == null) {
                if (next_node == null) {
                    if (head.compareAndSet(curr_node, null, false, false)) {
                        size.decrementAndGet();
                        return true;
                    }
                } else {
                    if (head.compareAndSet(curr_node, next_node, false, false)) {
                        size.decrementAndGet();
                        return true;
                    }
                }
            } else {
                if (prev_node.next.compareAndSet(curr_node, next_node, false, false)) {
                    size.decrementAndGet();
                    return true;
                }
            }
        }
    }

    public boolean contains(T value) {
        NodePair finded = find(value);
        AtomicMarkableReference<Node> prev = finded.second, curr = finded.first;
        if (curr.getReference().value.compareTo(value) == 0) {
            return true;
        }
        return false;
    }

    public boolean isEmpty() {
        return (size.get() == 0);
    }
}

