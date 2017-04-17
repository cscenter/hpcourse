import javafx.util.Pair;
import java.util.concurrent.atomic.AtomicMarkableReference;


public class LockFreeSetImpl<T extends Comparable<T>> implements LockFreeSet<T> {
    private class Node {
        Node(T value, Node next) {
            this.value = value;
            this.next = new AtomicMarkableReference<>(next, false);
        }

        T value;
        AtomicMarkableReference<Node> next;
    }

    private AtomicMarkableReference<Node> head = new AtomicMarkableReference<>(null, false);

    @Override
    public boolean add(T value) {
        Node newNode = new Node(value, null);

        while (true) {
            Pair<AtomicMarkableReference<Node>, Node> pair = find(value);
            AtomicMarkableReference<Node> curr = pair.getKey();
            Node next = pair.getValue();

            if (next != null && next.value.equals(value) && !next.next.isMarked()) {
                return false;
            }

            newNode.next.set(next, false);

            if (curr.compareAndSet(next, newNode, false, false))
                return true;
        }
    }

    @Override
    public boolean remove(T value) {
        while (true) {
            Pair<AtomicMarkableReference<Node>, Node> pair = find(value);
            AtomicMarkableReference<Node> prev = pair.getKey();
            Node curr = pair.getValue();

            if (curr == null || !curr.value.equals(value))
                return false;

            Node next = curr.next.getReference();

            if (!curr.next.compareAndSet(next, next, false, true))
                continue;

            prev.compareAndSet(curr, next, false, false);

            return true;
        }
    }

    @Override
    public boolean contains(T value) {
        Node node = find(value).getValue();
        return node != null && value.equals(node.value) && !node.next.isMarked();
    }

    private Pair<AtomicMarkableReference<Node>, Node> find(T value) {
        AtomicMarkableReference <Node> curr = head;
        Node next = head.getReference();
        while (next != null && next.value.compareTo(value) < 0) {
            curr = next.next;
            next = curr.getReference();
        }
        return new Pair<>(curr, next);
    }

    public boolean isEmpty() {
        return head.getReference() == null;
    }
}
