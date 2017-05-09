import java.util.concurrent.atomic.AtomicMarkableReference;

/**
 * Lock-Free множество.
 * @param <T> Тип ключей
 */
public class LockFreeSetImpl<T extends Comparable<T>> implements LockFreeSet<T>{
    private final Node head = new Node(null, null);

    private class Node {
        T value;
        AtomicMarkableReference<Node> next_and_flag;

        Node(T val, Node next_node) {
            next_and_flag = new AtomicMarkableReference<>(next_node, false);
            value = val;
        }

        boolean is_marked() {
            return next_and_flag.isMarked();
        }

        Node get_next() {
            return next_and_flag.getReference();
        }
    }

    private class Pair {
        Node prev, curr;
        Pair(Node p, Node c) {
            prev = p; curr = c;
        }
    }

    /**
     * Найти место, где находится / должно находиться value
     * Скорее всего сломается, если все элементы помечены как удалённые
     *
     * @param value значение ключа
     * @return пару из (pred = последний элемент с value < переданное value, его next)
     */
    private Pair find(T value) {
        retry: while(true) {
            Node pred = head, curr = head.get_next(), succ;
            while(true) {
                if (curr == null) {
                    // we found the tail
                    return new Pair(pred, null);
                }

                succ = curr.get_next();
                boolean current_marked = curr.is_marked();

                if (current_marked) {
                    // curr is marked as deleted, want to delete physically
                    boolean cas_res = pred.next_and_flag.compareAndSet(
                            curr, succ, false, false
                    );
                    if (!cas_res)
                        // something went wrong, need to start from beginning
                        continue retry;

                    // deleted successfully, moving ahead
                    curr = succ;
                } else {
                    // check if window is found
                    if (curr.value.compareTo(value) >= 0)
                        // found a window
                        return new Pair(pred, curr);

                    // make a step
                    pred = curr;
                    curr = succ;
                }
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
    public boolean add(T value) {
        while(true) {
            Pair found = find(value);
            Node pred = found.prev, curr = found.curr;
            if (curr != null && curr.value.compareTo(value) == 0) {
                // this value is already in the set
                return false;
            } else {
                Node node = new Node(value, curr);
                boolean added = pred.next_and_flag.compareAndSet(
                        curr, node, false, false
                );
                if (added)
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
    public boolean remove(T value) {
        while(true) {
            Pair found = find(value);
            Node pred = found.prev, curr = found.curr;
            if (curr == null || curr.value.compareTo(value) != 0) {
                // this value is not in set
                return false;
            } else {
                // mark as deleted
                Node next = curr.get_next();
                boolean cas_res = curr.next_and_flag.compareAndSet(
                        next, next, false, true
                );
                if (!cas_res)
                    // something went wrong, start from beginning
                    continue;

                // try to delete physically
                pred.next_and_flag.compareAndSet(
                        curr, next, false, false
                );
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
        Node curr = head.get_next();
        while (curr.get_next() != null && curr.value.compareTo(value) < 0) {
            curr = curr.get_next();
        }
        return curr.value.compareTo(value) == 0 && !curr.is_marked();
    }

    /**
     * Проверка множества на пустоту
     *
     * Алгоритм должен быть как минимум wait-free
     *
     * Даёт неверный ответ, если все элементы помечены как удалённые, но не удалены
     *
     * @return true если множество пусто, иначе - false
     */
    public boolean isEmpty() {
        return head.get_next() == null;
    }
}
