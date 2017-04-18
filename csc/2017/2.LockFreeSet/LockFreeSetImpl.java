import java.util.concurrent.atomic.AtomicReference;

public class LockFreeSetImpl<T extends Comparable<T>> implements LockFreeSet<T> {

  class Node {
    public final T data;
    public AtomicReference<Node> next;
    public boolean marked;

    public Node(T data, Node next, boolean marked) {
      this.data = data;
      this.next = new AtomicReference<>(next);
      this.marked = marked;
    }
  }

  private AtomicReference<Node> head = new AtomicReference<>(new Node(null, null, false));

  @Override
  public boolean isEmpty() {
    return head.get().next.get() == null;
  }

  @Override
  public boolean add(T value) {
    while (true) {
      AtomicReference<Node> pred = head, curr = head.get().next;

      while (curr.get() != null && curr.get().data.compareTo(value) < 0) {
        pred = curr;
        curr = curr.get().next;
      }

      if (curr.get() != null && curr.get().data.compareTo(value) == 0) {
        return false;
      }

      Node newNode = new Node(value, curr.get(), false);
      if (pred.get().next.compareAndSet(curr.get(), newNode)) {
        return true;
      }
    }
  }

  @Override
  public boolean remove(T value) {
    retry: while (true) {
      AtomicReference<Node> pred = head, curr = head.get().next;

      while (curr.get() != null && curr.get().data.compareTo(value) < 0) {
        pred = curr;
        curr = curr.get().next;
      }

      if (curr.get() == null || curr.get().data.compareTo(value) != 0) {
        return false;
      }

      AtomicReference<Node> succ = curr.get().next;
      if (!curr.compareAndSet(curr.get(), new Node(curr.get().data, curr.get().next.get(), false))) {
        continue retry;
      }

      pred.get().next.compareAndSet(curr.get(), succ.get());
      return true;
    }
  }

  @Override
  public boolean contains(T value) {
    AtomicReference<Node> pred = head, curr = head.get().next;

    while (curr.get() != null && curr.get().data.compareTo(value) < 0) {
      curr = curr.get().next;
    }

    return curr.get() != null && curr.get().data.compareTo(value) == 0;
  }

  public boolean validate() {
    if (head.get().data != null) {
      return false;
    }

    if (isEmpty()) {
      return true;
    }

    AtomicReference<Node> fstEl = head.get().next;
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
