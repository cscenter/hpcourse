import java.util.concurrent.atomic.AtomicReference;

public class LockFreeSetImpl<T extends Comparable<T>> implements LockFreeSet<T> {

  class Node {
    final T data;
    final AtomicReference<Node> next;

    Node(T data, Node next) {
      this.data = data;
      this.next = new AtomicReference<>(next);
    }
  }

  private Node head = new Node(null, null);

  @Override
  public boolean isEmpty() {
    return head.next.get() == null;
  }

  @Override
  public boolean add(T value) {
    while (true) {
      Node pred = head, curr = head.next.get();

      while (curr != null && curr.data.compareTo(value) < 0) {
        pred = curr;
        curr = curr.next.get();
      }

      if (curr != null && curr.data.compareTo(value) == 0) {
        return false;
      }

      Node newNode = new Node(value, curr);
      if (pred.next.compareAndSet(curr, newNode)) {
        return true;
      }
    }
  }

  @Override
  public boolean remove(T value) {
    while (true) {
      Node pred = head, curr = head.next.get();

      while (curr != null && curr.data.compareTo(value) < 0) {
        pred = curr;
        curr = curr.next.get();
      }

      if (curr == null || curr.data.compareTo(value) != 0) {
        return false;
      }

      if (pred.next.compareAndSet(curr, curr.next.get())) {
        return true;
      }
    }
  }

  @Override
  public boolean contains(T value) {
    Node curr = head.next.get();

    while (curr != null && curr.data.compareTo(value) < 0) {
      curr = curr.next.get();
    }

    return curr != null && curr.data.compareTo(value) == 0;
  }

  public boolean validate() {
    if (head.data != null) {
      return false;
    }

    if (isEmpty()) {
      return true;
    }

    AtomicReference<Node> fstEl = head.next;
    if (!isEmpty() && fstEl.get().data == null) {
      return false;
    }

    if (!isEmpty() && fstEl.get().next.get() == null) {
      return true;
    }

    AtomicReference<Node> pred = fstEl, curr = fstEl.get().next;
    while (curr.get() != null) {
      if (curr.get().data == null) {
        return false;
      }
      if (pred.get().data.compareTo(curr.get().data) > 0) {
        return false;
      }
      pred = curr;
      curr = curr.get().next;
    }

    return true;
  }
}
