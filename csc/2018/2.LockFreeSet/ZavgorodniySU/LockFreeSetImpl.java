import java.util.concurrent.atomic.AtomicMarkableReference;

/**
 * Realisation of Lock-free ordered list from https://habrahabr.ru/post/250383/
 * It can be used as Lock-free set, but with crappy asymptotics O(n)
 * <p>
 * Using two-phase delete from "A Pragmatic Implementation of Non-Blocking Linked Lists" T. Harris 2001
 */

public class LockFreeSetImpl<T extends Comparable<T>> implements LockFreeSet<T> {
    private final Node head = new Node(null, null);  // Head stub.

    /**
     * Insert key to set
     * First locate the previous node by search routine, then insert the node by CAS. If unsucceed, continue to locate.
     *
     * @param value key for insert
     * @return false if value already exists, true on success
     */
    @Override
    public boolean add(T value) {
        while (true) {
            Pair<Node> p = this.search(value);

            if (p.snd != null && p.snd.getValue().compareTo(value) == 0) {
                return false;
            }

            Node newNode = new Node(value, p.snd);
            if (p.fst.casNext(p.snd, newNode)) {
                return true;
            }
        }
    }

    /**
     * Delete key from set
     * <p>
     * Like the insert operation, it also locates the target node at the beginning. It removes the target node within two steps:
     * <p>
     * 1. Marks current node as deleted. If succeed, breaks the loop.
     * 2. Removes the node, if unsucceed, it indicates that other thread helps to remove the node.
     *
     * @param value key for remove
     * @return false if key wasn't found, true on success
     */
    @Override
    public boolean remove(T value) {
        while (true) {
            Pair<Node> p = this.search(value);

            if (p.snd == null || p.snd.getValue().compareTo(value) != 0) {
                // Not found
                return false;
            }

            Node nextNode = p.snd.getNext();
            // Logical deletion
            if (!p.snd.tryMarkAsDeleted()) {
                continue;
            }
            // Physical deletion. Don't care if it fails.
            p.fst.casNext(p.snd, nextNode);
            return true;
        }
    }

    /**
     * Check if key contains in set
     *
     * @param value is key
     * @return true if contains, otherwise false
     */
    @Override
    public boolean contains(T value) {
        Node cur = head.getNext();
        while (cur != null && cur.getValue().compareTo(value) < 0) {
            cur = cur.getNext();
        }

        return cur != null && cur.getValue().compareTo(value) == 0 && !cur.isDeleted();
    }

    /**
     * @return true if set is empty, otherwise false
     */
    @Override
    public boolean isEmpty() {
        return head.getNext() == null;
    }

    /**
     * Look for value in set
     *
     * @param value value which will be in foundNode or between prevNode and foundNode if not found.
     * @return Pair (prevNode, foundNode) For last node in set returns (lastNode, null)
     */
    private Pair<Node> search(T value) {
        Node prev = head;
        Node cur = head.getNext();
        Node next;

        while (true) {
            if (cur == null) {
                // Last element case
                return new Pair<>(prev, null);
            }

            next = cur.getNext();

            if (cur.isDeleted()) {
                // Help another thread with deleting
                if (!prev.casNext(cur, next)) {
                    // Restart search on cas fail
                    prev = head;
                    cur = head.getNext();
                    continue;
                }
                cur = next;
                continue;
            }

            if (cur.getValue().compareTo(value) >= 0) {
                // Found!
                return new Pair<>(prev, cur);
            }

            prev = cur;
            cur = next;
        }
    }

    private class Node {
        private T value;
        private AtomicMarkableReference<Node> nextRef;  // Tombstone mark (for current node!)

        Node(T value, Node next) {
            this.value = value;
            this.nextRef = new AtomicMarkableReference<>(next, false);
        }

        boolean casNext(Node expected, Node replacement) {
            return nextRef.compareAndSet(expected, replacement, false, false);
        }

        boolean isDeleted() {
            return this.nextRef.isMarked();
        }

        boolean tryMarkAsDeleted() {
            return this.nextRef.attemptMark(this.getNext(), true);
        }

        Node getNext() {
            return this.nextRef.getReference();
        }

        T getValue() {
            return value;
        }
    }

    private class Pair<E> {
        E fst, snd;

        Pair(E fst, E snd) {
            this.fst = fst;
            this.snd = snd;
        }
    }
}
