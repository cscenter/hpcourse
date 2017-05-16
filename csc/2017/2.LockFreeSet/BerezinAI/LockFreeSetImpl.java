/**
 * Created by beraliv on 3/05/17.
 */
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicMarkableReference;

/**
 * Реализация Lock-Free множества.
 * @param <T> Тип ключей, причем {@code Comparable<T>}
 */
public class LockFreeSetImpl<T extends Comparable<T>> implements LockFreeSet<T> {
    /**
     * Вспомогательный класс-вершина
     * @param <T> Тип ключей
     */
    static class Node<T> {
        T value;
        AtomicMarkableReference<Node<T>> next;

        Node() {
            this.value = null;
            this.next = new AtomicMarkableReference<>(null, false);
        }

        Node(T value, AtomicMarkableReference<Node<T>> next) {
            this.value = value;
            this.next = next;
        }

        /**
         * Static-метод, создающий итератор по списку вершин
         * @param head Начало списка
         * @param <T> Тип ключей
         * @return Итератор {@code Iterator<Node<T>>}
         */
        static <T> Iterator<Node<T>> iterator(Node<T> head) {
            return new Iterator<Node<T>>() {
                // private Node<T> previous = null;
                private Node<T> current = head;

                @Override
                public boolean hasNext() {
                    return current.next.getReference() != null;
                }

                @Override
                public Node<T> next() {
                    // previous = current;
                    return (current = current.next.getReference());
                }

                /**
                 * Не используется метод {@code remove()}
                 * @reason реализация требует, чтобы возвращалось {@code boolean} значение, а тут возвращает void
                 * @throws UnsupportedOperationException
                 */
                @Override
                public void remove() {
                    /**
                     * In case of implementing remove comment it
                     */
                    throw new UnsupportedOperationException("remove");
                    /**
                     * In case of implementing uncomment it
                     */
                    // previous.next.compareAndSet(current, current.next.getReference(), false, false);
                }
            };
        }
    }

    /**
     * Вспомогательное скользящее окно для поиска
     * @param <T>
     */
    static class SlidingWindow<T> {
        Node<T> previous, current;

        SlidingWindow(Node<T> previous, Node<T> current) {
            this.previous = previous;
            this.current = current;
        }

        /**
         * Static-метод, возвращающий скользящее окно по результату поиска значения в списке вершин
         * @param head Голова списка
         * @param value Запрашиваемое значение
         * @param <T> Тип ключей, причем {@code Comparable<T>}
         * @return Скользящее окно {@code SlidingWindow<T>}
         */
        static <T extends Comparable<T>> SlidingWindow<T> find(Node<T> head, T value) {
            fromBeginning: while (true) {
                Iterator<Node<T>> iterator = Node.iterator(head);
                Node<T> previous = head,
                        current = iterator.hasNext() ? iterator.next() : null;
                while (current != null && iterator.hasNext()) {
                    Node next = iterator.next();
                    if (current.next.isMarked()) {
                        if (!previous.next.compareAndSet(current, next, false, false)) {
                            continue fromBeginning;
                        }
                    } else {
                        if (current.value.compareTo(value) >= 0) {
                            return new SlidingWindow<>(previous, current);
                        }
                        previous = current;
                    }
                    current = next;
                }
                return new SlidingWindow<>(previous, current);
            }
        }
    }

    /**
     * Локальные переменные
     */
    private Node<T> head;

    public LockFreeSetImpl() {
        head = new Node<>();
    }

    @Override
    public boolean add(T value) {
        while (true) {
            SlidingWindow<T> window = SlidingWindow.find(head, value);
            Node<T> previous = window.previous,
                    current = window.current;
            // добавляемое значение уже существует, возвращаем отказ
            if (current != null && current.value.compareTo(value) == 0) {
                return false;
            }
            // если мы добавили, возвращаем успех
            Node<T> next = new Node<>(value, new AtomicMarkableReference<>(current, false));
            if (previous.next.compareAndSet(current, next, false, false)) {
                return true;
            }
        }
    }

    @Override
    public boolean remove(T value) {
        fromBeginning: while (true) {
            SlidingWindow<T> window = SlidingWindow.find(head, value);
            Node<T> previous = window.previous,
                    current = window.current;
            // ничего не нашли, или значение не то, возвращаем отказ
            if (current == null || current.value.compareTo(value) != 0) {
                return false;
            }
            // если не можем установить значение в true, попробуем начать сначала
            Node<T> next = current.next.getReference();
            if (!current.next.attemptMark(next, true)) {
                continue fromBeginning;
            }
            // если можем, то удаляем и возвращаем успех
            previous.next.compareAndSet(current, next, false, false);
            return true;
        }
    }

    @Override
    public boolean contains(T value) {
        Iterator<Node<T>> iterator = Node.iterator(head);
        Node<T> tail = null;
        // ищем элемент
        while (iterator.hasNext() && (tail = iterator.next()).value.compareTo(value) < 0) {}
        // проверяем, что есть следующий и он не залочен, тогда возвращаем успех
        return iterator.hasNext() && tail.value.compareTo(value) == 0 && !tail.next.isMarked();
    }

    @Override
    public boolean isEmpty() {
        Iterator<Node<T>> iterator = Node.iterator(head);
        // идем вперед и смотрим, что не залочен следующий элемент
        while (iterator.hasNext() && iterator.next().next.isMarked()) {}
        return !iterator.hasNext();
    }
}
