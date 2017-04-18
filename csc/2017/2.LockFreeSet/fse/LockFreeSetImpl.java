import java.util.concurrent.atomic.AtomicMarkableReference;

public class LockFreeSetImpl<T extends Comparable<T>> implements LockFreeSet<T> {

    private AtomicMarkableReference<Node> head
            = new AtomicMarkableReference<>(new Node(null), false);
    private AtomicMarkableReference<Node> tail =
            new AtomicMarkableReference<>(new Node(null), false);


    Pair search(T search_key) {
        AtomicMarkableReference<Node> left_node_next = tail, right_node;
        Pair result = new Pair(null, null);
        search_again:
        while (true) {
            AtomicMarkableReference<Node> t = head;
            AtomicMarkableReference<Node> t_next = head.getReference().next;
            do {
                if (!t_next.isMarked()) {
                    result.left = t;
                    left_node_next = t_next;
                }
                t = new AtomicMarkableReference<>(t_next.getReference(), false);
                if (t.getReference() == tail.getReference()) break;
                t_next = t.getReference().next;

            } while (t_next.isMarked() || (t.getReference().item.compareTo(search_key)) < 0);

            right_node = t;

            if (left_node_next == right_node)
                if ((right_node != tail) && right_node.getReference().next.isMarked())
                    break search_again; /*G1*/
                else{
                    result.curr = right_node;
                    return result;
                }

            if (result.left.getReference().next.compareAndSet(left_node_next.getReference(), right_node.getReference(), left_node_next.isMarked(),right_node.isMarked()))
            if ((right_node != tail) && right_node.getReference().next.isMarked())
                break search_again; /*G2*/
            else
            {
                result.curr = right_node;
                return result;
            }
        }
        return result;
    }

    @Override
    public boolean add(T key) {
        AtomicMarkableReference<Node> new_node = new AtomicMarkableReference<Node>(new Node(key),false);
        AtomicMarkableReference<Node> right_node, left_node;
        do {
            Pair res =  search (key);
            right_node = res.curr;
            left_node = res.left;
            if ((right_node.getReference() != tail.getReference()) && (right_node.getReference().item.compareTo(key)) == 0)
                return false;
            new_node.getReference().next = right_node;
            if (left_node.getReference().next.compareAndSet(right_node.getReference(), new_node.getReference(),right_node.isMarked(),new_node.isMarked())) /*C2*/
                return true;
        } while (true);
    }

    @Override
    public boolean remove(T search_key) {
        AtomicMarkableReference<Node> right_node, right_node_next, left_node;
        do {
            Pair res = search(search_key);
            right_node = res.curr;
            left_node = res.left;

            if ((right_node.getReference() == tail.getReference()) ||
                    (right_node.getReference().item.compareTo(search_key))!=0)
                return false;

            right_node_next = right_node.getReference().next;
            if (!right_node_next.isMarked())

                if ( right_node.getReference().next.compareAndSet(right_node_next.getReference(), right_node_next.getReference(),false,true))
                    break;
        } while (true);
        if (!left_node.getReference().next.compareAndSet( right_node.getReference(), right_node_next.getReference(), right_node.isMarked(), right_node_next.isMarked())) /*C4*/
        {
            Pair res = search(right_node.getReference().item);
            right_node = res.curr;
            left_node = res.left;
        }

        return true;
    }

    @Override
    public boolean contains(T search_key) {

        AtomicMarkableReference<Node> right_node;
        Pair res = search(search_key);
        right_node = res.curr;

        if ((right_node.getReference() == tail.getReference()) ||
                (right_node.getReference().item.compareTo(search_key))!=0)
            return false;
        else
            return true;
    }

    @Override
    public boolean isEmpty() {
        return head.getReference().next == tail;
    }

    private class Node {
        final T item;
        AtomicMarkableReference<Node> next;

        private Node(T item) {
            this.item = item;
        }
    }

    private class Pair {
        AtomicMarkableReference<Node> left, curr;

        Pair(AtomicMarkableReference<Node> left, AtomicMarkableReference<Node> curr) {
            this.left = left;
            this.curr = curr;
        }
    }

}
