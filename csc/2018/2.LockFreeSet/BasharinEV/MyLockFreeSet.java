import java.util.concurrent.atomic.AtomicMarkableReference;

/*
Implementation of Lock-free set.

Based on linked list. 
*/
public class MyLockFreeSet<T extends Comparable<T>>
implements LockFreeSet<T>
{

	/*
	Represents item of linked list.
	*/
	private class Node {
		final T value;
		final AtomicMarkableReference<Node> nextNodeRef;

		Node(T value, Node nextNode) {
			this.value = value;
			this.nextNodeRef = new AtomicMarkableReference<Node>(nextNode, false);
		}
	}

	/*
	Represents consequtive pair of nodes in linked list.
	*/
	private class CoupledNodePair
	{
		final Node prevNode;
		final Node curNode;

		CoupledNodePair(Node prev, Node cur) {
			this.prevNode = prev;
			this.curNode = cur;
		} 
	}

	public CoupledNodePair getCoupledNodePairByValue(T value)
	{

		boolean do_again = false;
		while (true)
		{
			/* First pair */
			Node prev = head;
			Node cur = head.nextNodeRef.getReference();

			/* Go through the list to search given value */
			while (cur != null)
			{
				/* if cur node is not marked and value equal or greater than given make coupled pair and return it. */
				if (!cur.nextNodeRef.isMarked() && cur.value.compareTo(value) >= 0)
					return new CoupledNodePair(prev, cur);

				Node nextNode = cur.nextNodeRef.getReference();
				if (cur.nextNodeRef.isMarked() && 
					!prev.nextNodeRef.compareAndSet(cur, nextNode, false, false)) {
					do_again = true;
					break;
				}

				prev = cur;
				cur = nextNode;
			}

			if (do_again) {
				do_again = false;
				continue;
			}

			return new CoupledNodePair(prev, cur);
		}
	}

	private final Node head = new Node(null, null);

	@Override
	public boolean add(T value) {
		while(true)
		{
			CoupledNodePair nodePair = getCoupledNodePairByValue(value);

			/* If value already exists return false */
			if (nodePair.curNode != null && nodePair.curNode.value.compareTo(value) == 0)
				return false;

			/* Add node with value */
			if (nodePair.prevNode != null && 
				nodePair.prevNode.nextNodeRef.compareAndSet(nodePair.curNode, new Node(value, nodePair.curNode), false, false))
				return true;
		}
	}

	@Override
	public boolean remove(T value) {
		while (true)
		{
			CoupledNodePair nodePair = getCoupledNodePairByValue(value);

			if (nodePair.curNode == null) return false;
			if (nodePair.curNode.value.compareTo(value) != 0) return false;

			assert nodePair.curNode.value.compareTo(value) == 0;

			Node next = nodePair.curNode.nextNodeRef.getReference();
			if (!nodePair.curNode.nextNodeRef.attemptMark(next, true)) continue;
			
			nodePair.prevNode.nextNodeRef.compareAndSet(nodePair.curNode, next, false, false);
			return true;
		}
	}

    @Override
    public boolean contains(T value) {
    	/* Just go through linked list */
    	Node cur = head.nextNodeRef.getReference();
    	while (cur != null)
    	{
    		int cmp_res = cur.value.compareTo(value);
    		if (cmp_res == 0 && !cur.nextNodeRef.isMarked()) return true;
    		if (cmp_res > 0) return false;
    		cur = cur.nextNodeRef.getReference();
    	}
    	return false;
    }

    @Override
    public boolean isEmpty() {
    	return head.nextNodeRef.getReference() == null;
    }
}