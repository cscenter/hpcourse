import java.util.concurrent.atomic.AtomicMarkableReference;

public class LockFreeSetImpl<T extends Comparable<T>> implements LockFreeSet<T> {

    private final Node head = new Node(null, (new Node(null, null)));

    private class Node {
        public final AtomicMarkableReference<Node> next;
        public final Comparable value;

        public Node(final Comparable value, final Node next) {
            this.value = value;
            this.next = new AtomicMarkableReference<>(next, false);
        }
    }

    private class SearchResult {
        final Node previous;
        final Node current;

        SearchResult(final Node previous, final Node current) {
            this.previous = previous;
            this.current = current;
        }
    }

    @Override
    public boolean add(final Comparable value) {
        while (true) {
            final SearchResult searchResult = find(value);

            if (searchResult.current.value == null || value.compareTo(searchResult.current.value) != 0) {
                Node node = new Node(value, searchResult.current);

                // Trying to add created element
                if (searchResult.previous.next.compareAndSet(searchResult.current, node, false, false)) {
                    return true;
                }
            } else {
                return false;
            }
        }
    }

    @Override
    public boolean remove(final Comparable value) {
        while (true) {
            final SearchResult searchResult = find(value);

            if (searchResult.current.value != null && value.compareTo(searchResult.current.value) == 0) {

                final Node next = searchResult.current.next.getReference();

                // Trying to mark element as read
                if (!searchResult.current.next.attemptMark(next, true)) {
                    continue;
                }

                // Trying to remove element physically
                // Not interested in the result, could delete this node during the next search
                searchResult.previous.next.compareAndSet(searchResult.current, next, false, false);

                return true;
            } else {
                return false;
            }
        }
    }

    // Don't call find. Contains should be wait-free
    @Override
    public boolean contains(final Comparable value) {
        boolean[] isDeleted = {false};
        Node current = head.next.get(isDeleted);

        while (current.value != null && value.compareTo(current.value) > 0) {
            current = current.next.get(isDeleted);
        }

        return current.value != null && value.compareTo(current.value) == 0 && !isDeleted[0];
    }

    @Override
    public boolean isEmpty() {
        return head.next.getReference().value == null;
    }

    public SearchResult find(final Comparable value) {
        tryAgain:

        while (true) {

            Node previous = head;
            Node current = head.next.getReference();

            while (true) {
                boolean[] isDeleted = {false};
                Node next = current.next.get(isDeleted);

                // Trying to delete marked element physically
                while (isDeleted[0]) {
                    if (!previous.next.compareAndSet(current, next, false, false)) {
                        continue tryAgain;
                    }
                    current = next;
                    next = current.next.get(isDeleted);
                }

                // Getting element if it is the right place for searched element
                if (current.value == null || value.compareTo(current.value) <= 0) {
                    return new SearchResult(previous, current);
                }

                previous = current;
                current = next;
            }
        }
    }

}