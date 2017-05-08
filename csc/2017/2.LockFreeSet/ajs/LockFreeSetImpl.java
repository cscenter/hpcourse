import java.util.concurrent.atomic.AtomicMarkableReference;

public class LockFreeSetImpl<T extends Comparable<T>> implements LockFreeSet<T> {

	private Node<T> head = new Node<T>(null);

	@Override
	public boolean add(T value) {
		while (true) {
			Pair<Node<T>> pair = find(value);
			if ((pair.second != null)
					&& (pair.second.value.compareTo(value) == 0)) {
				return false;
			}
			Node<T> newNode = new Node<T>(value, pair.second);
			if (pair.first.next.compareAndSet(pair.second, newNode, false,
					false)) {
				return true;
			}
		}
	}

	@Override
	public boolean remove(T value) {
		while (true) {
			Pair<Node<T>> pair = find(value);
			if ((pair.second == null)
					|| (pair.second.value.compareTo(value) != 0)) {
				return false;
			}
			Node<T> next = pair.second.next.getReference();
			if (!pair.second.next.compareAndSet(next, next, false, true)) {
				continue;
			}
			pair.first.next.compareAndSet(pair.second, next, false, false);
			return true;
		}

	}

	@Override
	public boolean contains(T value) {
		Node<T> curr = head.next.getReference();
		while ((curr != null) && (curr.value.compareTo(value) <= 0)) {
			if ((curr.value.compareTo(value) == 0) && (!curr.next.isMarked())) {
				return true;
			}
			curr = curr.next.getReference();
			;
		}
		return false;
	}

	@Override
	public boolean isEmpty() {
		Node<T> curr = head.next.getReference();
		while (curr != null) {
			if (!curr.next.isMarked()) {
				return false;
			}
			curr = curr.next.getReference();
		}
		return true;
	}

	private Pair<Node<T>> find(T value) {
		while (true) {
			Node<T> pred = head;
			Node<T> curr = pred.next.getReference();
			Node<T> succ = null;
			while (true) {
				if (curr == null) {
					return new Pair<Node<T>>(pred, curr);
				}
				succ = curr.next.getReference();
				boolean cmk = curr.next.isMarked();
				if (cmk) {
					if (!pred.next.compareAndSet(curr, succ, false, false)) {
						break;
					}
				} else {
					if (curr.value.compareTo(value) >= 0) {
						return new Pair<Node<T>>(pred, curr);
					}
					pred = curr;
				}
				curr = succ;
			}
		}

	}

	private class Pair<E> {
		E first;
		E second;

		Pair(E first, E second) {
			this.first = first;
			this.second = second;
		}
	}

	private class Node<E extends Comparable<E>> {
		final E value;
		AtomicMarkableReference<Node<E>> next;

		Node(final E value) {
			this(value, null);
		}

		Node(final E value, Node<E> next) {
			this.value = value;
			this.next = new AtomicMarkableReference<Node<E>>(next, false);
		}

	}

}