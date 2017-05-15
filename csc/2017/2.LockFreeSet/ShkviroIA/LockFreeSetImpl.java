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
        NodePair(Node first, Node second) {
            this.first = first;
            this.second = second;
        }

        Node first;
        Node second;
    }

    public LockFreeSetImpl() {
        this.head = new AtomicMarkableReference<Node>(null, false);
        this.size = new AtomicInteger(0);
    }

    private AtomicMarkableReference<Node> head;
    private AtomicInteger size;

    NodePair find(T value) {
        Node curr = head.getReference(), prev = null;
        boolean marked = head.isMarked();
        while (curr != null) {
            if (!marked) {
                if (curr.value.compareTo(value) == 0) {
                    return new NodePair(curr, prev);
                } else {
                    prev = curr;
                }
            }
            curr = curr.next.getReference();
            if (curr != null) {
                marked = curr.next.isMarked();
            }
        }
        return new NodePair(curr, prev);
    }

    @Override
    public boolean add(T value) {
        while (true) {
            NodePair finded = find(value);
            Node curr = finded.first, prev = finded.second;
            if (curr != null && curr.value.compareTo(value) == 0) {
                return false;
            }

            Node new_node = new Node(value,  new AtomicMarkableReference<Node>(null, false));
            if (prev == null) {
                if (head.compareAndSet(null, new_node, false, false)) {
                    size.incrementAndGet();
                    return true;
                }
            } else {
                if (prev.next.compareAndSet(curr, new_node, false, false)) {
                    size.incrementAndGet();
                    return true;
                }
            }
        }
    }

    public boolean remove(T value) {
        while (true) {
            NodePair finded = find(value);
            Node curr = finded.first, prev = finded.second;
            if (curr == null || curr.value.compareTo(value) != 0) {
                return false;
            }

            Node next = curr.next.getReference();
            if (prev == null) {
                if (head.attemptMark(curr, true)) {
                    head.compareAndSet(curr, next, true, false);
                    size.decrementAndGet();
                    return true;
                }
            } else {
                if (prev.next.attemptMark(curr, true)) {
                    prev.next.compareAndSet(curr, next, true, false);
                    size.decrementAndGet();
                    return true;
                }
            }
        }
    }

    public boolean contains(T value) {
        NodePair finded = find(value);
        Node curr = finded.first, prev = finded.second;
        if (curr != null && curr.value.compareTo(value) == 0) {
            return true;
        }
        return false;
    }

    public boolean isEmpty() {
        return (size.get() == 0);
    }
}