import java.util.concurrent.atomic.AtomicMarkableReference;

public class LockFreeSetImpl<T extends Comparable<T>> implements LockFreeSet<T> {

  private final Node<T> head;

  public LockFreeSetImpl() {
    head = new Node<>(null, new Node<T>(null, null));
  }

  @Override
  public boolean add(final T value) {
    while (true) {
      final Window<T> window = findWindow(value);
      final Node<T> pred = window.pred;
      final Node<T> cur = window.cur;
      if (cur.value != null && cur.value.compareTo(value) == 0) {
        return false;
      }

      final Node<T> node = new Node<>(value, cur);
      if (pred.nextRef.compareAndSet(cur, node, false, false)) {
        return true;
      }
    }
  }

  @Override
  public boolean remove(final T value) {
    while (true) {
      final Window<T> window = findWindow(value);
      final Node<T> pred = window.pred;
      final Node<T> cur = window.cur;
      if (cur.value == null || cur.value.compareTo(value) != 0) {
        return false;
      }

      final Node<T> succ = cur.nextRef.getReference();
      if (!cur.nextRef.compareAndSet(cur.nextRef.getReference(), cur.nextRef.getReference(), false, true)) {
        continue;
      }
      pred.nextRef.compareAndSet(cur, succ, false, false);
      return true;
    }
  }

  @Override
  public boolean contains(final T value) {
    Node<T> cur = head.nextRef.getReference();
    while (cur != null && cur.value != null && cur.value.compareTo(value) < 0) {
      cur = cur.nextRef.getReference();
    }
    return cur != null
            && cur.value != null
            && !cur.nextRef.isMarked()
            && cur.value.compareTo(value) == 0;
  }

  @Override
  public boolean isEmpty() {
    return head.nextRef.getReference().value == null;
  }

  private Window<T> findWindow(final T value) {
    retry:
    while (true) {
      Node<T> pred = head;
      Node<T> cur = pred.nextRef.getReference();
      Node<T> succ;
      while (true) {
        succ = cur.nextRef.getReference();
        if (cur.nextRef.isMarked()) {
          if (!pred.nextRef.compareAndSet(cur, succ, false, false)) {
            continue retry;
          }
          cur = succ;
        } else {
          if (cur.value == null || cur.value.compareTo(value) >= 0) {
            return new Window<>(pred, cur);
          }
          pred = cur;
          cur = succ;
        }
      }
    }
  }

  private static class Node<T extends Comparable<T>> {
    final AtomicMarkableReference<Node<T>> nextRef;
    final T value;

    Node(final T value, final Node<T> next) {
      nextRef = new AtomicMarkableReference<>(next, false);
      this.value = value;
    }
  }

  private static class Window<T extends Comparable<T>> {
    final Node<T> pred;
    final Node<T> cur;

    Window(final Node<T> pred, final Node<T> cur) {
      this.pred = pred;
      this.cur = cur;
    }
  }
}