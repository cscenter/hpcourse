/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package cscenter;

import java.util.concurrent.atomic.AtomicMarkableReference;

/**
 *
 * @author CX70
 */
public class SimpleLockFreeSet<T extends Comparable<T>> implements LockFreeSet<T> {

	class Node
	{
		AtomicMarkableReference<Node> next = new AtomicMarkableReference<Node>(null, false);
		Comparable value;
	}
	Node head = new Node();
	
	class Pair
	{
		Node prev, next;
	}
	
	@Override
	public boolean add(Comparable value) {
		retry:
		while(true)
		{
			Pair v = find(value);
			if(v.next != null && v.next.value.compareTo(value) == 0)
			{
				return false;
			}
			else
			{
				Node next = v.next;
				Node newNode = new Node();
				newNode.value = value;
				newNode.next = new AtomicMarkableReference(next, false);
				if(!v.prev.next.compareAndSet(next, newNode, false, false))
					continue retry;
				return true;
			}
		}
	}

	@Override
	public boolean remove(Comparable value) {
		retry:
		while (true)
		{
			Pair v = find(value);
			if(v.next == null || v.next.value.compareTo(value) != 0)
			{
				return false;
			}
			else
			{
				Node succ = (v.next.next != null) ? v.next.next.getReference() : null;
				if(v.next.next != null && !v.next.next.attemptMark(succ, true))
					continue retry;
				v.prev.next.compareAndSet(v.next, succ, false, false);
				return true;
			}
			
		}
	}

	@Override
	public boolean contains(Comparable value) {
		Node curr = head.next.getReference(), prev = head;
		while (curr != null && curr.value.compareTo(value) < 0) {
			prev = curr;
			curr = curr.next.getReference();
		}
		return curr != null && value.compareTo(curr.value) == 0 && !curr.next.isMarked();
	}

	@Override
	public boolean isEmpty() {
		return head.next.getReference() == null;
	}
	
	Pair find(Comparable value)
	{
		retry: 
		while(true) {
			Node prev = head, curr = (head.next != null) ? prev.next.getReference() : null, succ;
			while (curr != null) {
				succ = curr.next.getReference();
				if (prev.next.isMarked()) { 
					if(!prev.next.compareAndSet(curr, succ, false, false))
						continue retry;
					curr = succ;
				} 
				else {
					if (curr.value.compareTo(value) >= 0) 
					{
						Pair p = new Pair();
						p.prev = prev;
						p.next = curr;
						return p;
					}
					prev = curr; curr = succ;
				}
			}
			Pair p = new Pair();
			p.prev = prev;
			p.next = curr;
			return p;
		}
	}
	
}
