import java.util.concurrent.atomic.AtomicMarkableReference;

class Node<T extends Comparable<T>>
{
    final T value;
    final AtomicMarkableReference<Node<T>> markedNext;

    Node(final T value, final Node<T> next) {
        this.value = value;
        this.markedNext = new AtomicMarkableReference<Node<T>>(next, false);
    }

    boolean isNextMarkedDeleted() {
        return markedNext.isMarked();
    }

    Node<T> next() {
        return markedNext.getReference();
    }

    int compareWith(T value) {
        return this.value.compareTo(value);
    }

    boolean equalsTo(T value) {
        return this.compareWith(value) == 0;
    }
}
