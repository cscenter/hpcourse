import java.util.concurrent.atomic.AtomicMarkableReference;

public class LockFreeSetImpl<T extends Comparable<T>> implements LockFreeSet<T> {

    private final AtomicMarkableReference<Node> tail =
            new AtomicMarkableReference(new Node(null), false);

    private final AtomicMarkableReference<Node> head
            = new AtomicMarkableReference(
            new Node(null,tail.getReference(),false),
            false
    );

    SearchResult search(T search_key) {
        AtomicMarkableReference<Node> left_node = new AtomicMarkableReference<>(null,false);
        AtomicMarkableReference<Node> right_node;
        AtomicMarkableReference<Node> left_node_next = new AtomicMarkableReference<>(null,false);
        search_again:
        while (true) {
            AtomicMarkableReference<Node> t = head;
            AtomicMarkableReference<Node> t_next = head.getReference().next;
            do {
                if (!t_next.isMarked()) {
                    left_node = t;
                    left_node_next = t_next;
                }
                t = new AtomicMarkableReference(t_next.getReference(), false);
                if (t.getReference() == tail.getReference()) break;
                t_next = t.getReference().next;

            } while (t_next.isMarked() || (t.getReference().item.compareTo(search_key)) < 0);

            right_node = t;

            if (left_node_next.getReference() == right_node.getReference())
                if ((right_node.getReference() != tail.getReference()) && right_node.getReference().next.isMarked())
                    break search_again;
                else {
                    return new SearchResult(left_node,right_node);
                }

            if (left_node.getReference().next.compareAndSet(left_node_next.getReference(), right_node.getReference(), left_node.getReference().next.isMarked(), left_node.getReference().next.isMarked()))
                if ((right_node.getReference() != tail.getReference()) && right_node.getReference().next.isMarked())
                    break search_again;
                else {
                    return new SearchResult(left_node,right_node);
                }
        }
        return null;
    }


    public boolean add(T key) {
        AtomicMarkableReference<Node> new_node = new AtomicMarkableReference<>(new Node(key), false);
        AtomicMarkableReference<Node> right_node, left_node;
        do {
            SearchResult res = search(key);
            right_node = res.right;
            left_node = res.left;
            if ((right_node.getReference() != tail.getReference()) && (right_node.getReference().item.compareTo(key)) == 0)
                return false;
            new_node.getReference().next = right_node;
            if (left_node.getReference().next.compareAndSet(right_node.getReference(), new_node.getReference(), right_node.isMarked(), new_node.isMarked()))
                return true;
        } while (true);
    }


    public boolean remove(T search_key) {
        AtomicMarkableReference<Node> right_node, right_node_next, left_node;
        do {
            SearchResult res = search(search_key);
            right_node = res.right;
            left_node = res.left;

            if ((right_node.getReference() == tail.getReference()) ||
                    (right_node.getReference().item.compareTo(search_key)) != 0)
                return false;

            right_node_next = right_node.getReference().next;
            if (!right_node_next.isMarked())

                if (right_node.getReference().next.compareAndSet(right_node_next.getReference(), right_node_next.getReference(), right_node_next.isMarked(), true))
                    break;
        } while (true);
        if (!left_node.getReference().next.compareAndSet(right_node.getReference(), right_node_next.getReference(), left_node.isMarked(), left_node.isMarked()))
            search(right_node.getReference().item);
        return true;
    }


    public boolean contains(T search_key) {

        AtomicMarkableReference<Node> right_node;
        SearchResult res = search(search_key);
        right_node = res.right;

        if ((right_node.getReference() == tail.getReference()) ||
                (right_node.getReference().item.compareTo(search_key)) != 0)
            return false;
        else
            return true;
    }


    public boolean isEmpty() {
        return head.getReference().next.getReference() == tail.getReference();
    }

    private class Node {
        final T item;
        AtomicMarkableReference<Node> next;

        public Node(T item) {
            this.item=item;
            this.next=new AtomicMarkableReference<>(null,false);
        }

        public Node(T item, Node nextNode, boolean mark) {
            this.item=item;
            this.next=new AtomicMarkableReference<>(nextNode,mark);
        }
    }

    private class SearchResult{
        AtomicMarkableReference<Node> left, right;

        SearchResult(AtomicMarkableReference<Node> left, AtomicMarkableReference<Node> right) {
            this.left = left;
            this.right = right;
        }
    }
}
