import java.util.concurrent.atomic.AtomicMarkableReference;

public class LockFreeSetImpl<T extends Comparable<T>> implements LockFreeSet<T>
{

    //impl details
    private static final int HEAD = 1;
    private static final int TAIL = 2;
    private static final int ITEM = 3;

    private class Node 
    {
        T item;
        final int type; // 1 - head, 2 - tail, 3 - item
        AtomicMarkableReference<Node> next;
        
        /**
         * Constructor for usual node
         * @param item element in set
         */
        Node(T item, int type) 
        {
            this.item = item;
            this.type = type;
            this.next = new AtomicMarkableReference<Node>(null, false);
        }

        /**
         * constructor for head/tail creating
         * @param type binary type for head/tail
         */
        Node(int type) 
        {
            this.item = null;
            this.type = type;
            this.next = new AtomicMarkableReference<Node>(null, false);
        }
    }


    class Pair {

        public Node left;
        public Node curr;

        Pair(Node left, Node curr)
        {
            this.left = left;
            this.curr = curr;
        }
    }

    /**
     * @param head start of list
     * @param wanted_item is item to search for
     * @return If element with key is in the list, returns Pair of curr and left of node(predecessor). Else returns
     * Pair of curr with next item (in order of sorting) and left.
     */
    public Pair search(Node head, T wanted_item)
    {
        Node left = null;
        Node curr = null;
        Node right = null;

        boolean[] marked_for_cas = {false}; // is curr marked for cas
        boolean cas_result = false;

        traverse_list_label:
        while (true)
        {
            left = head;
            curr = left.next.getReference();

            while (true)
            {
                right = curr.next.get(marked_for_cas);

                while (marked_for_cas[0])
                {
                    cas_result = left.next.compareAndSet(curr, right, false, false);

                    if (!cas_result)
                        continue traverse_list_label;

                    curr = left.next.getReference();
                    right = curr.next.get(marked_for_cas);
                }

                if (curr.item == null || curr.item.compareTo(wanted_item) >= 0)
                    return new Pair(left, curr);

                left = curr;
                curr = right;
            }
        }
    }



    //Implementation

    private Node head;
    private Node tail;
    
    public LockFreeSetImpl()
    {
        this.head  = new Node(HEAD);
        this.tail = new Node(TAIL);
        while (!head.next.compareAndSet(null, tail, false, false));
    }

    public boolean add(T item)
    {
        while (true)
        {
            Pair pair_lc = search(head, item);
            Node left = pair_lc.left;
            Node curr = pair_lc.curr;

            if (curr.item == item)
                return false;
            else
            {
                Node node = new Node(item, ITEM);
                node.next = new AtomicMarkableReference(curr, false);

                if (left.next.compareAndSet(curr, node, false, false))
                    return true;
            }
        }
    }

    public boolean remove(T item)
    {
        boolean marked_for_cas;

        while (true)
        {
            Pair pair_lc = search(head, item);
            Node left = pair_lc.left;
            Node curr = pair_lc.curr;

            if (curr.item != item)
                return false;
            else
            {
                Node right = curr.next.getReference();
                marked_for_cas = curr.next.attemptMark(right, true);

                if (!marked_for_cas)
                    continue;

                left.next.compareAndSet(curr, right, false, false);
                return true;
            }
        }
    }

    public boolean contains(T item)
    {
        Pair pair_lc = search(head, item);
        Node curr = pair_lc.curr;

        return (curr.item == item);
    }

    public boolean isEmpty()
    {
        return (this.head.next.getReference() == this.tail);
    }

/*
    public void print()
    {
        Node head = this.head;
        Node curr = head.next.getReference();
        while (curr != this.tail)
        {
            if(curr.item != null)
                System.out.println(curr.item);

            curr = curr.next.getReference();
        }
    }
*/
}