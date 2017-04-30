//package rf.local;

import javafx.util.Pair;
import java.util.concurrent.atomic.AtomicReference;

public class LockFreeSetImpl implements LockFreeSet {

    private AtomicReference<Node> head = new AtomicReference<>(new Node(null, null));

    private class State {
        private boolean isDeleted;
        private Node next;

        public State(boolean isDeleted, Node next) {
            this.isDeleted = isDeleted;
            this.next = next;
        }

        public boolean Deleted() {
            return isDeleted;
        }

        public Node Next() {
            return next;
        }
    }

    private class Node {
        Comparable key;
        AtomicReference<State> state;

        public Node(Comparable key, Node next) {
            this.key = key;
            this.state = new AtomicReference<>(new State(false, next));
        }
    }

    private Pair<Node, Node> find(Comparable value) {
        retry: while (true) {
            Node prev = head.get();
            State prevState = prev.state.get();
            Node curr = prevState.Next();

            while (true) {
                if (curr == null)
                    return new Pair<>(prev, curr);

                State currState = curr.state.get();

                if (currState.Deleted()){
                    Node succ = currState.Next();
                    State newPrevState = new State(false, succ);

                    if (!prev.state.compareAndSet(prevState, newPrevState))
                        continue retry;

                    prevState = newPrevState;
                    curr = prevState.Next();
                    continue;
                }

                if (curr.key.compareTo(value) >= 0) {
                    return new Pair<>(prev, curr);
                }

                prev = curr;
                prevState = prev.state.get();
                curr = prevState.Next();
            }
        }
    }

    @Override
    public boolean add(Comparable key) {
        while (true) {
            Pair<Node, Node> res = find(key);
            Node prev = res.getKey(), curr = res.getValue();

            if (curr != null && curr.key.compareTo(key) == 0)
                return false;

            State prevState = prev.state.get();
            if (prevState.Next() != curr)
                continue;

            Node added = new Node(key, curr);
            State newState = new State(prevState.Deleted(), added);
            if (prev.state.compareAndSet(prevState, newState))
                return true;
        }
    }

    @Override
    public boolean remove(Comparable key) {
        while (true) {
            Pair<Node, Node> result = find(key);
            Node prev = result.getKey(), curr = result.getValue();
            if (curr == null || curr.key.compareTo(key) != 0) {
                return false;
            }

            State prevState = prev.state.get();
            if (prevState.Next() != curr)
                continue;

            State currState = curr.state.get();
            if (currState.Deleted())
                return false;

            State deletedState = new State(true, currState.Next());
            if (curr.state.compareAndSet(currState, deletedState))
                return true;
        }
    }

    @Override
    public boolean contains(Comparable value) {
        Node prev = head.get();
        Node curr = prev.state.get().Next();

        while (true) {
            if (curr == null)
                return false;

            int comparisonResult = curr.key.compareTo(value);

            if (comparisonResult == 0)
                return !curr.state.get().Deleted();

            if (comparisonResult > 0) {
                return false;
            }

            prev = curr;
            curr = prev.state.get().Next();
        }
    }

    @Override
    public boolean isEmpty() {

        Node prev = head.get();
        Node curr = prev.state.get().Next();
        while (true) {
            if (curr == null)
                return true;

            if (!curr.state.get().Deleted())
                return false;

            prev = curr;
            curr = prev.state.get().Next();
        }
    }
}
