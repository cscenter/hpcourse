import java.util.concurrent.atomic.AtomicMarkableReference;

/**
 * Lock-Free множество.
 *
 * @param <T> Тип ключей
 */
public class LockFreeSetImpl<T extends Comparable<T>> implements LockFreeSet<T> {

    private class Node<U extends Comparable<U>> {
        final U value;
        AtomicMarkableReference<Node<U>> next;

        Node(U val, Node nxt) {
            value = val;
            next = new AtomicMarkableReference<>(nxt, false);
        }

        U getValue() {
            return value;
        }

        Node<U> getNext() {
            return next.getReference();
        }


        boolean isMarked() {
            return next.isMarked();
        }

        void mark() {
            next.compareAndSet(this, this, false, true);
        }
    }

    private Node<T> head = null;

    /**
     * Найти наибольший элемент в множестве, строго меньший данного
     *
     * @param value искомое значение
     * @return указатель на наибольший элемент в множестве, строго меньший данного, если такой есть, или null в противном случае
     */
    private Node<T> lowerBound(T value) {
        if (isEmpty() || head.getValue().compareTo(value) >= 0) {
            return null;
        }

        for (Node<T> curr = head; curr != null; curr = curr.getNext()) {
            Node<T> next = curr.getNext();

            if (next == null || (!curr.isMarked() && curr.getValue().compareTo(value) < 0 && next.getValue().compareTo(value) >= 0)) {  // either we found lower bound or reached the end of the set
                return curr;
            }
        }

        return null;
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
        if (contains(value)) {
            return false;
        }

        Node<T> lb = lowerBound(value);
        if (lb == null) {
            Node<T> newHead = new Node<>(value, head);
            head = newHead;
        } else {
            Node<T> next = lb.getNext();
            Node<T> newNode = new Node<>(value, next);
            lb.next.compareAndSet(next, newNode, false, false);
        }

        return true;
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
        if(head != null && !head.isMarked() && head.getValue().compareTo(value) == 0) {
            head = null;
            return true;
        }

        Node<T> lb = lowerBound(value);
        if (lb == null || lb.getNext() == null || lb.getNext().getValue().compareTo(value) != 0) {
            return false;
        } else {
            lb.getNext().mark();
            lb.next.compareAndSet(
                    lb.getNext(), lb.getNext().getNext(), false, false
            );
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
        if (head != null && head.getValue().compareTo(value) == 0) {
            return true;
        }

        Node<T> lb = lowerBound(value);
        return lb != null && lb.getNext() != null && !lb.getNext().isMarked() && lb.getNext().getValue().compareTo(value) == 0;
    }

    /**
     * Проверка множества на пустоту
     * <p>
     * Алгоритм должен быть как минимум wait-free
     *
     * @return true если множество пусто, иначе - false
     */
    public boolean isEmpty() {
        for (Node<T> curr = head; curr != null; curr = curr.getNext()) {
            if (!curr.isMarked()) {
                return false;
            }
        }

        return true;
    }
}