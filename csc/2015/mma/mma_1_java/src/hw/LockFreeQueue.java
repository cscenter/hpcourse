package hw;

import java.util.concurrent.atomic.AtomicReference;

// http://titan.fsb.hr/~drunje/papers/iti2007.pdf
public class LockFreeQueue<T> implements IQueue<T> {
    private AtomicReference<Node> head;
    private AtomicReference<Node> tail;

    public LockFreeQueue() {
        Node sentinel = new Node(null);
        this.head = new AtomicReference<>(sentinel);
        this.tail = new AtomicReference<>(sentinel);
    }

    private static <T> boolean CAS(AtomicReference<T> target, T expect, T update) {
        return target.compareAndSet(expect, update);
    }

    public boolean isEmpty() {
        while (true) {
            Node oldHead = head.get();
            Node oldHeadNext = oldHead.next.get();
            Node oldTail = tail.get();
            // consistent
            if (oldHead == head.get()) {
                if (oldHead == oldTail) {
                    return oldHeadNext == null;
                }
                return false;
            }
        }
    }

    public void add(T item) {
        Node oldTail;
        Node newTail = new Node(item);
        while (true) {
            oldTail = tail.get();
            Node oldTailNext = oldTail.next.get();
            // consistent
            if (oldTail == tail.get()) {
                if (oldTailNext == null) {
                    if (CAS(oldTail.next, null, newTail)) {
                        break;
                    }
                } else {
                    CAS(tail, oldTail, oldTailNext);
                }
            }
        }
        CAS(tail, oldTail, newTail);
    }

    public T poll() {
        while (true) {
            Node oldHead = head.get();
            Node oldHeadNext = oldHead.next.get();
            Node oldTail = tail.get();
            // consistent
            if (oldHead == head.get()) {
                if (oldHead == oldTail) {
                    if (oldHeadNext == null) {
                        //queue is empty
                        return null;
                    }
                    CAS(tail, oldTail, oldHeadNext);
                } else {
                    T value = oldHeadNext.value;
                    if (CAS(head, oldHead, oldHeadNext))
                        return value;
                }
            }
        }
    }

    private class Node {
        public T value;
        public AtomicReference<Node> next;

        public Node(T value) {
            this.value = value;
            this.next = new AtomicReference<Node>(null);
        }
    }
}