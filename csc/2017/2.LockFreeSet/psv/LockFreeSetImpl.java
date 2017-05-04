import java.util.concurrent.atomic.AtomicMarkableReference;

/**
 * Lock-Free множество.
 * @param <T> Тип ключей
 */
public class LockFreeSetImpl<T extends Comparable<T> > implements LockFreeSet<T> {
    private final Node head = new Node(null, null);

    private class Node {
        final T value;
        final AtomicMarkableReference<Node> next;

        Node(T value, Node next) {
            this.value = value;
            this.next = new AtomicMarkableReference<>(next, false);
        }

        Node getNextNode() {
            return next.getReference();
        }

        boolean isMarked() {
            return next.isMarked();
        }
    }

    private class Pair {
        final Node prev;
        final Node curr;

        Pair(Node prev, Node curr) {
            this.prev = prev;
            this.curr = curr;
        }
    }

    private Pair find(T value) {
        while(true) {
            Node prev = head;
            Node curr = head.getNextNode();
            while(true) {
                if (curr == null) {
                    final Pair res = new Pair(prev, null);
                    return res;
                }
                final Node next = curr.getNextNode();
                if (curr.isMarked()) {
                    if (!prev.next.compareAndSet(curr, next, false, false)) {
                        break;
                    }
                } else if (curr.value.compareTo(value) >= 0) {
                    final Pair res = new Pair(prev, curr);
                    return res;
                }
                prev = curr;
                curr = next;
            }
        }
    }

    /**
     * Добавить ключ к множеству
     *
     * Алгоритм должен быть как минимум lock-free
     *
     * @param value значение ключа
     * @return false если value уже существует в множестве, true если элемент был добавлен
     */
    @Override
    public boolean add(T value) {
        while (true) {
            final Pair pair = find(value);
            Node prev = pair.prev;
            Node curr = pair.curr;
            if (curr != null && curr.value.equals(value)) {
                return false;
            }
            final Node node = new Node(value, curr);
            if (prev.next.compareAndSet(curr, node, false, false)) {
                return true;
            }
        }
    }

    /**
     * Удалить ключ из множества
     *
     * Алгоритм должен быть как минимум lock-free
     *
     * @param value значение ключа
     * @return false если ключ не был найден, true если ключ успешно удален
     */
    @Override
    public boolean remove(T value) {
        while (true) {
            final Pair pair = find(value);
            final Node prev = pair.prev;
            final Node curr = pair.curr;
            if (curr == null || !curr.value.equals(value)) {
                return false;
            }
            final Node next = curr.getNextNode();
            if (curr.next.attemptMark(next, true)) {
                prev.next.compareAndSet(curr, next, false, false);
                return true;
            }
        }
    }

    /**
     * Проверка наличия ключа в множестве
     *
     * Алгоритм должен быть как минимум wait-free
     *
     * @param value значение ключа
     * @return true если элемент содержится в множестве, иначе - false
     */
    public boolean contains(T value) {
        Node curr = head.getNextNode();
        while (curr != null && curr.value.compareTo(value) < 0) {
            curr = curr.getNextNode();
        }
        return curr != null && !curr.isMarked() && curr.value.equals(value);
    }

    /**
     * Проверка множества на пустоту
     *
     * Алгоритм должен быть как минимум wait-free ?
     *
     * @return true если множество пусто, иначе - false
     */
    @Override
    public boolean isEmpty() {
        return head.getNextNode() == null;
    }
}

