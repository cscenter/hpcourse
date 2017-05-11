import java.util.concurrent.atomic.AtomicMarkableReference;

public class LockFreeSetImpl<T extends Comparable<T>> implements LockFreeSet<T> {

  class Node {
    final T data;
    final AtomicMarkableReference<Node> next;

    Node(T data, Node next) {
      this.data = data;
      this.next = new AtomicMarkableReference<>(next, false);
    }
  }

  private Node head = new Node(null, null);
  private boolean [] markHolder = new boolean[1];

  @Override
  public boolean isEmpty() {
    AtomicMarkableReference<Node> curr = head.next;

    while (curr.get(markHolder) != null) {
      if (!markHolder[0]) {
        return false;
      }
      curr = curr.getReference().next;
    }

    return true;
  }

  @Override
  public boolean add(T value) {
    while (true) {
      Node pred = head, curr = head.next.get(markHolder);

      while (curr != null && curr.data.compareTo(value) < 0) {
        pred = curr;
        curr = curr.next.get(markHolder);
      }

      if (curr != null && curr.data.compareTo(value) == 0) {
        return false;
      }

      Node newNode = new Node(value, curr);
      if (pred.next.compareAndSet(
          curr,
          newNode,
          markHolder[0],
          false)
          ) {
        return true;
      }
    }
  }

  @Override
  public boolean remove(T value) {
    while (true) {
      AtomicMarkableReference<Node> curr = head.next;

      while (curr.getReference() != null && curr.getReference().data.compareTo(value) < 0) {
        curr = curr.getReference().next;
      }

      if (curr.getReference() == null || curr.getReference().data.compareTo(value) != 0) {
        return false;
      }

      Node currNode = curr.get(markHolder);
      if (!curr.compareAndSet(currNode, currNode, markHolder[0], true)) {
        continue;
      }

      return true;
    }
  }

  @Override
  public boolean contains(T value) {
    Node curr = head.next.get(markHolder);

    while (curr != null && curr.data.compareTo(value) < 0) {
      curr = curr.next.get(markHolder);
    }

    return curr != null && !markHolder[0] && curr.data.compareTo(value) == 0;
  }
}
