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
            next.compareAndSet(getNext(), getNext(), false, true);
        }
    }

    private class Pair<U extends Comparable<U>> {
        final Node<U> pred;
        final Node<U> curr;

        Pair(Node<U> pred, Node<U> curr) {
            this.pred = pred;
            this.curr = curr;
        }
    }

    private Node<T> preHead = new Node<>(null, null);   // should never be null

    /**
     * Найти наибольший элемент в множестве, строго меньший данного, и наименьший элемент, не меньший данного
     *
     * @param value искомое значение
     * @return указатель на пару элементов в множестве, если такие есть, или null в противном случае
     */
    private Pair<T> bounds(T value) {
        retry: while(true) {

            Node pred = preHead;
            Node curr = pred.getNext();
            Node succ;

            while (true) {
                if(curr == null) {
                    return new Pair<>(pred, null);
                }

                succ = curr.getNext();

                if (curr.isMarked()) {
                    if(!pred.next.compareAndSet(curr, succ, false, false)) {
                        continue retry;
                    }

                    curr = succ;

                } else {
                    if (curr.getValue().compareTo(value) >= 0) {
                        return new Pair<>(pred, curr);
                    }

                    pred = curr;
                    curr = succ;
                }
            }
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
        while(true) {
            Pair<T> p = bounds(value);

            Node<T> pred = p.pred;
            Node<T> curr = p.curr;

            if (curr != null && value.compareTo(curr.getValue()) == 0) {
                return false;
            }

            Node<T> newNode = new Node<>(value, curr);

            if(pred.next.compareAndSet(curr, newNode, false, false)) {
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
        retry: while (true) {
            Pair<T> p = bounds(value);

            Node<T> pred = p.pred;
            Node<T> curr = p.curr;

            if (curr == null || curr.getValue().compareTo(value) != 0) {
                return false;
            }

            Node<T> succ = curr.getNext();
            if (!curr.next.compareAndSet(succ, succ, false, true)) {
                continue retry;
            }
            pred.next.compareAndSet(curr, succ, false, false);

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
        Pair<T> p = bounds(value);
        Node<T> curr = p.curr;

        return (curr != null && value.compareTo(curr.getValue()) == 0);
    }

    /**
     * Проверка множества на пустоту
     * <p>
     * Алгоритм должен быть как минимум wait-free
     *
     * @return true если множество пусто, иначе - false
     */
    public boolean isEmpty() {
        return preHead.getNext() == null;
    }
}