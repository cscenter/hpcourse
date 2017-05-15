package ru.cscenter.hpcource.lockfreeset;

import java.util.concurrent.atomic.AtomicMarkableReference;

public class LockFreeSetImpl<T extends Comparable<T>> implements LockFreeSet<T> {

    private final Node<T> head = new Node<>(null, null);

    private static class Node<T> {
        final T value;
        final AtomicMarkableReference<Node<T>> nextReference;

        Node(T value, Node<T> next) {
            this.value = value;
            nextReference = new AtomicMarkableReference<>(next, false);
        }

    }

    /**
     * Добавить ключ к множеству
     * <p>
     * Алгоритм должен быть как минимум lock-free
     *
     * @param value значение ключа
     * @return false если value уже существует в множестве, true если элемент был добавлен
     */
    public boolean add(T value) {
        while (true) {
            Node<T>[] nodes = find(value);
            if (nodes[1] != null && nodes[1].value.equals(value)) {
                return false;
            }
            Node<T> tail = new Node<>(value, nodes[1]);
            if (nodes[0].nextReference.compareAndSet(nodes[1], tail, false, false)) {
                return true;
            }
        }
    }

    /**
     * Удалить ключ из множества
     * <p>
     * Алгоритм должен быть как минимум lock-free
     *
     * @param value значение ключа
     * @return false если ключ не был найден, true если ключ успешно удален
     */
    public boolean remove(T value) {
        while (true) {
            Node<T>[] nodes = find(value);

            if (nodes[1] == null || nodes[1].value.compareTo(value) != 0) {
                return false;
            }

            Node<T> tail = nodes[1].nextReference.getReference();
            if (!nodes[1].nextReference.attemptMark(tail, true)) {
                continue;
            }
            nodes[0].nextReference.compareAndSet(nodes[1], tail, false, false);
            return true;
        }
    }

    /**
     * Проверка наличия ключа в множестве
     * <p>
     * Алгоритм должен быть как минимум wait-free
     *
     * @param value значение ключа
     * @return true если элемент содержится в множестве, иначе - false
     */
    public boolean contains(T value) {
        Node<T> found = head.nextReference.getReference();

        while (found != null && found.value.compareTo(value) < 0) {
            found = found.nextReference.getReference();
        }

        return found != null && found.value.compareTo(value) == 0 && !found.nextReference.isMarked();
    }

    /**
     * Проверка множества на пустоту
     * <p>
     * Алгоритм должен быть как минимум wait-free
     *
     * @return true если множество пусто, иначе - false
     */
    public boolean isEmpty() {
        return head.nextReference.getReference() == null;
    }

    private Node<T>[] find(T value) {
        retry:
        while (true) {
            Node<T> prev = head;
            Node<T> curr = head.nextReference.getReference();
            Node<T> tmp;
            while (curr != null) {
                tmp = curr.nextReference.getReference();
                if (curr.nextReference.isMarked()) {
                    if (!prev.nextReference.compareAndSet(curr, tmp, false, false)) {
                        continue retry;
                    }
                } else {
                    if (curr.value.compareTo(value) >= 0) {
                        return new Node[] {prev, curr};
                    }
                    prev = curr;
                }
                curr = tmp;
            }
            return new Node[] {prev, curr};
        }
    }
}
