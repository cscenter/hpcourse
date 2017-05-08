import java.util.concurrent.atomic.AtomicMarkableReference;

public final class LockFreeListSet<T extends Comparable<T>> implements LockFreeSet<T> {

  private final static class Node<T extends Comparable<T>> {
    private final T item;

    private final AtomicMarkableReference<Node<T>> nextAndMark;

    Node(final T item, final Node<T> next) {
      this.item = item;
      this.nextAndMark = new AtomicMarkableReference<>(next, false);
    }

    T value() {
      return item;
    }

    AtomicMarkableReference<Node<T>> nextAndMark() {
      return nextAndMark;
    }
  }

  private final static class Window<T extends Comparable<T>> {
    private final Node<T> pred;
    private final Node<T> curr;

    private Window(final Node<T> pred,
                   final Node<T> curr) {
      this.pred = pred;
      this.curr = curr;
    }

    Node<T> pred() {
      return pred;
    }

    Node<T> curr() {
      return curr;
    }
  }

  private final Node<T> head;
  private final Node<T> tail;

  @SuppressWarnings({"unchecked", "WeakerAccess"})
  public LockFreeListSet() {
    //Dirty hacks
    final Comparable<T> MAX_INF = o -> 1;
    this.tail = new Node(MAX_INF, null);

    final Comparable<T> MIN_INF = o -> -1;
    this.head = new Node(MIN_INF, this.tail);
  }

  private Window<T> find(final T value) {
    retry:
    while (true) {
      Node<T> pred = head;
      Node<T> curr = pred.nextAndMark().getReference();
      while (true) {
        if (!curr.nextAndMark().isMarked()) {
          if (curr.value().compareTo(value) >= 0) {
            return new Window<>(pred, curr);
          } else {
            pred = curr;
            curr = curr.nextAndMark().getReference();
          }
        } else {
          if (pred.nextAndMark().compareAndSet(curr, curr.nextAndMark().getReference(), false, false)) {
            curr = curr.nextAndMark().getReference();
          } else {
            continue retry;
          }
        }
      }
    }
  }

  @Override
  public boolean add(final T value) {
    while (true) {
      final Window<T> window = find(value);
      Node<T> pred = window.pred();
      Node<T> curr = window.curr();

      if (curr.value().equals(value)) {
        return false;
      } else {
        final Node<T> newNode = new Node<>(value, curr);

        if (pred.nextAndMark().compareAndSet(curr, newNode, false, false)) {
          return true;
        } else {
          System.err.println("ADD CAS FAILED");
          //retry
        }
      }
    }
  }

  @Override
  public boolean remove(final T value) {
    while (true) {
      final Window<T> window = find(value);
      Node<T> curr = window.curr();

      if (!curr.value().equals(value)) {
        return false;
      } else {
        if (curr.nextAndMark().attemptMark(curr.nextAndMark().getReference(), true)) {
          return true;
        } else {
          System.err.println("REMOVE CAS FAILED");
          //retry
        }
      }
    }
  }

  @Override
  public boolean contains(final T value) {
    Node<T> curr = head;
    while (curr.value().compareTo(value) < 0) {
      curr = curr.nextAndMark().getReference();
    }

    return value.equals(curr.value()) && !curr.nextAndMark().isMarked();
  }

  @Override
  public boolean isEmpty() {
    //probably wont work
    return head.nextAndMark().getReference() == tail;
  }
}
