package kornilova.set;

import java.util.concurrent.atomic.AtomicMarkableReference;

class Node<T> {
    final T myValue;
    final AtomicMarkableReference<Node<T>> myNext;

    Node(T value, Node<T> next) {
        myValue = value;
        myNext = new AtomicMarkableReference<>(next, false);
    }

    @Override
    public String toString() {
        return String.format("Node(value=%s)", myValue);
    }
}
