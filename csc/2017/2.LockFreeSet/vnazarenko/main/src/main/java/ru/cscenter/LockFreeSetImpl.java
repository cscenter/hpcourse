package ru.cscenter;

import java.util.AbstractMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicMarkableReference;

/**
 * Created by Vladimir Nazarenko on 17.04.17.
 */
public class LockFreeSetImpl<T extends Comparable<T>> implements LockFreeSet<T> {

    class Node {
        public Node(T item, Node next) {
            this.item = item;
            this.next = new AtomicMarkableReference<>(next, false);
        }
        final T item;
        final AtomicMarkableReference<Node> next;
    }

    final Node backward_guard = new Node(null, null);
    final Node forward_guard = new Node(null, backward_guard);



    /*
      Finds a pair of elements, second being greater or equal than item and first preceding the second
     */
    Map.Entry<Node, Node> find(T item) {
        assert forward_guard.next.getReference() != null;

        retry: while (true) {
            Node prev = forward_guard, curr = forward_guard.next.getReference();
            while (true) {
                boolean[] isMarked = new boolean[1];
                Node succ = curr.next.get(isMarked);
                boolean marked = isMarked[0];
                if (marked) {
                    if (!prev.next.compareAndSet(curr, succ, false, false))
                        continue retry;
                    curr = succ;
                } else {
                    if (curr == backward_guard || curr.item.compareTo(item) >= 0)
                        return new AbstractMap.SimpleEntry<>(prev, curr);
                    prev = curr;
                    curr = succ;
                }
            }
        }
    }


    @Override
    public boolean add(T value) {
        if (value == null)
            return false;
        while (true) {
            Node prev, curr;
            Map.Entry<Node, Node> pair = find(value);
            prev = pair.getKey();
            curr = pair.getValue();
            if (curr != backward_guard && value.compareTo(curr.item) == 0)
                return false;
            else {
                Node node = new Node(value, curr);
                if (prev.next.compareAndSet(curr, node, false, false))
                    return true;
            }
        }
    }

    @Override
    public boolean remove(T value) {
        if (value == null) {
            return false;
        }
        while (true) {
            Map.Entry<Node, Node> pair = find(value);
            Node prev = pair.getKey(), curr = pair.getValue();
            if (curr == backward_guard || curr.item.compareTo(value) != 0)
                return false;
            else {
                Node succ = curr.next.getReference();
                if (!curr.next.attemptMark(succ, true))
                    continue;
                prev.next.compareAndSet(curr, succ, false, false);
                return true;
            }
        }
    }

    @Override
    public boolean contains(T value) {
        if (value == null) {
            return false;
        }
        Node curr = forward_guard.next.getReference();
        while (curr != backward_guard && curr.item.compareTo(value) < 0) {
            curr = curr.next.getReference();
        }

        return curr != backward_guard && curr.item.compareTo(value) == 0 && !curr.next.isMarked();
    }

    @Override
    public boolean isEmpty() {
        return forward_guard.next.getReference() == backward_guard;
    }
}
