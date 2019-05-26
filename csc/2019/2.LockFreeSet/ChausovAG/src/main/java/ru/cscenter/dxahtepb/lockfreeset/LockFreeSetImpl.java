package ru.cscenter.dxahtepb.lockfreeset;

import java.util.concurrent.atomic.AtomicMarkableReference;

public class LockFreeSetImpl<T extends Comparable<T>> implements LockFreeSet<T> {
  private final Node head = new Node(null, null);

  @Override
  public boolean add(final T value) {
    while (true) {
      final Pair pair = find(value);
      final Node prev = pair.prevNode;
      final Node curr = pair.nextNode;

      if (curr == null || curr.value.compareTo(value) != 0) {
        final Node node = new Node(value, curr);
        if (prev.next.compareAndSet(curr, node, false, false)) {
          return true;
        }
      } else {
        return false;
      }
    }
  }

  @Override
  public boolean remove(final T value) {
    while (true) {
      final Pair pair = find(value);
      final Node prev = pair.prevNode;
      final Node curr = pair.nextNode;

      if (curr == null || curr.value.compareTo(value) != 0) {
        return false;
      } else {
        final Node succ = curr.next.getReference();
        if (!curr.next.compareAndSet(succ, succ, false, true)) {
          continue;
        }
        prev.next.compareAndSet(curr, succ, false, false);
        return true;
      }
    }

  }

  @Override
  public boolean contains(final T value) {
    final boolean[] holder = new boolean[1];
    Node curr = head.next.get(holder);
    while (curr != null && curr.value.compareTo(value) < 0) {
      curr = curr.next.get(holder);
    }
    final boolean isCurrMarked = holder[0];
    return curr != null && curr.value.compareTo(value) == 0 && !isCurrMarked;
  }

  private Pair find(final T value) {
    retry:
    while (true) {
      Node prev = head;
      Node curr = prev.next.getReference();

      while (true) {
        if (curr == null) {
          return new Pair(prev, null);
        }

        final boolean[] holder = new boolean[1];
        final Node succ = curr.next.get(holder);
        final boolean isCurrMarked = holder[0];

        if (isCurrMarked && !prev.next.compareAndSet(curr, succ, false, false)) {
          continue retry;
        } else {
          if (!isCurrMarked && value.compareTo(curr.value) <= 0) {
            return new Pair(prev, curr);
          }
          prev = curr;
          curr = succ;
        }
      }
    }
  }

  @Override
  public boolean isEmpty() {
    return head.next.getReference() == null;
  }

  @Override
  public java.util.Iterator<T> iterator() {
    return null;
  }

  private class Node {
    final T value;
    final AtomicMarkableReference<Node> next;

    Node(final T value, final Node next) {
      this.value = value;
      this.next = new AtomicMarkableReference<>(next, false);
    }

    @Override
    public String toString() {
      return "Node(" + (value != null ? value.toString() : "null") + ")";
    }
  }

  private class Pair {
    final Node prevNode;
    final Node nextNode;

    Pair(final Node prevNode, final Node nextNode) {
      this.prevNode = prevNode;
      this.nextNode = nextNode;
    }

    @Override
    public String toString() {
      return "Pair("
              + (prevNode != null ? prevNode.toString() : "null")
              + ", "
              + (nextNode != null ? nextNode.toString() : "null")
              + ")";
    }
  }
}
