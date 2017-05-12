import java.util.concurrent.atomic.AtomicMarkableReference;

public class LockFreeSetImpl<T extends Comparable<T>> implements LockFreeSet<T> {

    final private Node tail = new Node(null, null);
    final private Node head = new Node(null, tail);

    SearchResult search(T search_key) {
        retry:
        while (true) {
            Node left = head;
            Node right = head.next.getReference();
            Node succ;
            boolean[] t = new boolean[1];
            while (true) {
                succ = right.next.get(t);
                boolean cmk = t[0];
                if (cmk) {
                    if (!left.next.compareAndSet(right, succ, false, false))
                        continue retry;
                    right = succ;
                } else {
                    if (right == tail || right.item.compareTo(search_key) >= 0)
                        return new SearchResult(left, right);
                    left = right;
                    right = succ;
                }
            }
        }
    }

    public boolean add(T key) {
        Node right_node, left_node;
        do {
            SearchResult res = search(key);
            left_node = res.left;
            right_node = res.right;

            if ((right_node != tail) && (right_node.item.compareTo(key)) == 0)
                return false;
            Node new_node = new Node(key, right_node);
            if (left_node.next.compareAndSet(right_node, new_node, false, false))
                return true;
        } while (true);
    }

    public boolean remove(T search_key) {
        Node right_node, right_node_next, left_node;
        do {
            SearchResult res = search(search_key);
            left_node = res.left;
            right_node = res.right;

            if ((right_node == tail) ||
                    (right_node.item.compareTo(search_key)) != 0)
                return false;

            right_node_next = right_node.next.getReference();
            if (!right_node.next.isMarked())
                if (right_node.next.compareAndSet(right_node_next, right_node_next, false, true))
                    break;
        } while (true);

        left_node.next.compareAndSet(right_node, right_node_next, false, false);
        return true;
    }

    public boolean contains(T search_key) {

        Node right_node = head;
        do {
            right_node = right_node.next.getReference();
        } while (right_node != tail && right_node.item.compareTo(search_key) < 0);

        return right_node != tail &&
                right_node.item.compareTo(search_key) == 0 &&
                !right_node.next.isMarked();
    }

    public boolean isEmpty() {
        return head.next.getReference() == tail;
    }

    private class Node {
        final T item;
        final AtomicMarkableReference<Node> next;

        public Node(T item, Node nextNode) {
            this.item = item;
            this.next = new AtomicMarkableReference<>(nextNode, false);
        }
    }

    private class SearchResult {
        final Node left, right;

        SearchResult(Node left, Node right) {
            this.left = left;
            this.right = right;
        }
    }
}
