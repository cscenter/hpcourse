import java.util.concurrent.atomic.AtomicMarkableReference;

public class LockFreeSetImpl<T extends Comparable<T>> implements LockFreeSet<T> {

    private final Node head;

    public LockFreeSetImpl() {
        head = new Node(null, new AtomicMarkableReference<>(null, false));
    }

    private class Node {
        final T value;
        final AtomicMarkableReference<Node> next_element;

        Node(T val, AtomicMarkableReference<Node> next) {
            this.value = val;
            this.next_element = next;
        }
    }

    private class SearchProcess {
        final Node prev;
        final Node curr;

        SearchProcess(T value) {

            TryOnceMore:
            while (true) {

                prev = head;
                curr = head.next_element.getReference();

                while (curr != null) {

                    Node next = curr.next_element.getReference();

                    if (curr.next_element.isMarked()) {

                        if (!prev.next_element.compareAndSet(curr, next, false, false))
                            continue TryOnceMore;

                        curr = next;

                    } else {

                        if (curr.value.compareTo(value) >= 0)
                            return;

                        prev = curr;
                        curr = next;
                    }
                }
                return;
            }
        }
    }


    @Override
    public boolean add(T value) {

        while (true) {

            SearchProcess pair = new SearchProcess(value);

            Node curr = pair.curr;

            if (curr != null && curr.value.compareTo(value) == 0) {
                return false;
            } else {
                Node newNode = new Node(value, new AtomicMarkableReference<>(curr, false));
                if (pair.prev.next_element.compareAndSet(curr, newNode, false, false)) {
                    return true;
                }
            }
        }
    }

    @Override
    public boolean remove(T value) {

        while (true) {

            SearchProcess pair = new SearchProcess(value);
            Node curr = pair.curr;

            if (curr == null || curr.value.compareTo(value) != 0) {
                return false;
            } else {
                Node succ = curr.next_element.getReference();
                if (curr.next_element.attemptMark(succ, true)) {
                    pair.prev.next_element.compareAndSet(curr, succ, false, false);
                    return true;
                }
            }
        }
    }

    @Override
    public boolean contains(T value) {

        Node node = head.next_element.getReference();

        while (node != null && node.value.compareTo(value) < 0) {
            node = node.next_element.getReference();
        }

        return node != null && node.value.compareTo(value) == 0 && !node.next_element.isMarked();
    }

    @Override
    public boolean isEmpty() {
        return head.next_element.getReference() == null;
    }
}
