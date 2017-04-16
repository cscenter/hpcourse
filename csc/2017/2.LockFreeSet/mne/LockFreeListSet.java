import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class LockFreeListSet<T extends Comparable<T>> implements LockFreeSet<T> {

  private final static class Node<T extends Comparable<T>> {
    private final T item;

    private final AtomicReference<NextAndMarked<T>> nextAndMarked;

    Node(final T item, final Node<T> next) {
      this.item = item;
      this.nextAndMarked = new AtomicReference<>(new NextAndMarked<>(next, false));
    }

    T value() {
      return item;
    }

    AtomicReference<NextAndMarked<T>> nextAndMarked() {
      return nextAndMarked;
    }
  }

  private final static class NextAndMarked<T extends Comparable<T>> {
    private final Node<T> next;

    private final boolean marked;

    NextAndMarked(final Node<T> next, final boolean marked) {
      this.next = next;
      this.marked = marked;
    }

    Node<T> next() {
      return next;
    }

    boolean isMarked() {
      return marked;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      final NextAndMarked<?> that = (NextAndMarked<?>) o;
      return marked == that.marked &&
              Objects.equals(next, that.next);
    }
  }

  private final static class Window<T extends Comparable<T>> {
    private final Node<T> pred;
    private final Node<T> curr;

    private final NextAndMarked<T> predNextAndMarked;
    private final NextAndMarked<T> currNextAndMarked;

    private Window(final Node<T> pred,
                   final NextAndMarked<T> predNextAndMarked,
                   final Node<T> curr,
                   final NextAndMarked<T> currNextAndMarked) {
      this.pred = pred;
      this.curr = curr;
      this.predNextAndMarked = predNextAndMarked;
      this.currNextAndMarked = currNextAndMarked;
    }

    NextAndMarked<T> currNextAndMarked() {
      return currNextAndMarked;
    }

    NextAndMarked<T> predNextAndMarked() {
      return predNextAndMarked;
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
      NextAndMarked<T> predNextAndMarked = pred.nextAndMarked().get();
      Node<T> curr = predNextAndMarked.next();
      while (true) {
        final NextAndMarked<T> currNextAndMarked = curr.nextAndMarked().get();

        if (!currNextAndMarked.isMarked()) {
          if (curr.value().compareTo(value) >= 0) {
            return new Window<>(pred, predNextAndMarked, curr, currNextAndMarked);
          } else {
            pred = curr;
            predNextAndMarked = currNextAndMarked;
            curr = currNextAndMarked.next();
          }
        } else {
          final NextAndMarked<T> newPredNextAndMarked = new NextAndMarked<>(currNextAndMarked.next(), false);

          if (pred.nextAndMarked().compareAndSet(predNextAndMarked, newPredNextAndMarked)) {
            predNextAndMarked = newPredNextAndMarked;
            curr = currNextAndMarked.next();
          } else {
            //System.err.println("Retry CAS failed");
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

        if (pred.nextAndMarked().compareAndSet(window.predNextAndMarked(), new NextAndMarked<>(newNode, false))) {
          return true;
        } else {
          //System.err.println("Add CAS failed");
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
        final NextAndMarked<T> newCurrentNextAndMarked = new NextAndMarked<>(window.currNextAndMarked().next(), true);

        if (curr.nextAndMarked().compareAndSet(window.currNextAndMarked(), newCurrentNextAndMarked)) {
          return true;
        } else {
          //System.err.println("Remove CAS failed");
          //retry
        }
      }
    }
  }

  @Override
  public boolean contains(final T value) {
    Node<T> curr = head;
    while (curr.value().compareTo(value) < 0) {
      curr = curr.nextAndMarked.get().next();
    }

    return value.equals(curr.value()) && !curr.nextAndMarked().get().isMarked();
  }

  @Override
  public boolean isEmpty() {
    //probably wont work
    return head.nextAndMarked().get().next() == tail;
  }
}
