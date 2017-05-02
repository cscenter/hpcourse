import java.util.ArrayList;
import java.util.concurrent.atomic.*;

/**
 * Java Mission Control показывала после flight recording на 4 потоках с барьером,
 * что потоки друг друга не ждут и выполняются практически параллельно.
 */

public class LockFreeSetImpl<T extends Comparable<T>> implements LockFreeSet<T> {

    private class State {
        final Node next; // both fields are final for object to represent state
        final boolean marked; // true if node is deleted

        State(final Node next, boolean marked) {
            this.next = next;
            this.marked = marked;
        }
    }

    class Node {
        final T key;
        final AtomicReference<State> state = new AtomicReference<>();

        Node(final T value, final State initState) {
            key = value;
            state.set(initState);
        }
    }

    // head of connected list
    final AtomicReference<Node> head = new AtomicReference<>();

    public LockFreeSetImpl() {
        head.set(new Node(null, new State(null, true)));
    }

    /**
     * Добавить ключ к множеству
     * <p>
     * Алгоритм должен быть как минимум lock-free
     *
     * @param value значение ключа
     * @return false если value уже существует в множестве, true если элемент был добавлен
     */
    @Override
    public boolean add(T value) {
        while (true) {
            if (this.isEmpty()) {
                AtomicReference<Node> oldHead = new AtomicReference<>(head.get());
                State oldHeadState = oldHead.get().state.get();
                if (!oldHeadState.marked || oldHeadState.next != null) continue; // if not empty
                Node newHead = new Node(value, new State(null, false));
                if (head.compareAndSet(oldHead.get(), newHead)) {
                    return true;
                }
                continue;
            }
            ArrayList<Node> searchResult = find(value);
            Node pred = searchResult.get(0);
            Node current = searchResult.get(1);
            if (current != null && current.key.equals(value)) return false;
            else {
                AtomicReference<State> reference = pred.state;
                if (reference.get().marked || reference.get().next != current)
                    continue; // someone deleted element we standing on
                Node node = new Node(value, new State(current, false));
                if (pred.state.compareAndSet(reference.get(), new State(node, false))) {
                    return true;
                }
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
    @Override
    public boolean remove(T value) {
        while (true) {
            if (this.isEmpty()) return false;
            ArrayList<Node> results = find(value);
            Node pred = results.get(0);
            Node current = results.get(1);
            if (current == null || !current.key.equals(value)) {
                return false;
            } else {
                AtomicReference<State> reference = current.state;
                if (reference.get().marked) { // not existing logically
                    continue;
                }
                if (!current.state.compareAndSet(reference.get(), new State(reference.get().next, true))) // deleting logically
                    continue; // if didn't succeeded -- retry
                if (pred == null){ // current is head
                    Node newHead = current.state.get().next;
                    if (newHead == null){
                        newHead = new Node(null, new State(null, true));
                    }
                    head.compareAndSet(current, newHead);
                    return true;
                }
                reference = pred.state;
                if (!reference.get().marked && reference.get().next == current) { // if only we submitted changes
                    pred.state.compareAndSet(reference.get(), new State(current.state.get().next, false)); // delete physically if possible
                }
                return true; // anyway logically or physically we deleted element
            }
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
    @Override
    public boolean contains(T value) {
        Node current = head.get();
        // while not found or exceeded value and didn't reach end
        while (current != null && current.key != null
                && current.key.compareTo(value) < 0 && current.state.get().next != null) {
            current = current.state.get().next;
        }
        return value.equals(current.key) && !current.state.get().marked;
    }

    /**
     * Проверка множества на пустоту
     * <p>
     * Алгоритм должен быть как минимум wait-free
     *
     * @return true если множество пусто, иначе - false
     */
    @Override
    public boolean isEmpty() {
        Node currentHead = head.get();
        return currentHead.state.get().marked && currentHead.key == null;
    }


    private ArrayList<Node> find(T value) {
        search:
        while (true) {
            Node pred = head.get();
            Node current = head.get().state.get().next;
            while (true) {
                if (pred.key.equals(value)) return getResult(null, pred); // if found in head
                if (current == null) return getResult(pred, current); // reached end of connected list
                AtomicReference<State> currState = current.state;
                AtomicReference<State> predState = pred.state;
                if (predState.get().marked || predState.get().next != current) continue search;
                if (currState.get().marked) { // if deleted
                    if (!pred.state.compareAndSet(predState.get(), new State(currState.get().next, false))) // do cleaning
                        continue search; // start search again, because someone changed set outside
                    current = currState.get().next; // otherwise we succeeded in cleaning, keep iterating
                } else {
                    if (current.key.compareTo(value) >= 0) return getResult(pred, current);
                    pred = current;
                    current = currState.get().next;
                }
            }
        }
    }

    private ArrayList<Node> getResult(final Node pred, final Node current) {
        ArrayList<Node> result = new ArrayList<>();
        result.add(pred);
        result.add(current);
        return result;
    }
}
