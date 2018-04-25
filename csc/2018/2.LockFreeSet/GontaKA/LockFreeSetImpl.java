import java.util.concurrent.atomic.AtomicMarkableReference;

public class LockFreeSetImpl<T extends Comparable<T>> implements LockFreeSet<T> {

    private class Node {
        private final T value;
        private final AtomicMarkableReference<Node> currentFlagAndNext;

        Node(T value, Node next) {
            this.value = value;
            currentFlagAndNext = new AtomicMarkableReference<>(next, false);
        }
    }

    private class Pair {
        final Node currentNode;
        final Node nextNode;

        Pair(Node currentNode, Node nextNode) {
            this.currentNode = currentNode;
            this.nextNode = nextNode;
        }
    }

    private final Node head = new Node(null, null);

    public boolean add(T value) {
        while (true) {
            Pair pair = find(value);
            Node currentNode = pair.currentNode;
            Node nextNode = pair.nextNode;
            if (nextNode != null && nextNode.value.compareTo(value) == 0) {
                // Element already exists
                return false;
            }
            Node node = new Node(value, nextNode);
            if (currentNode.currentFlagAndNext.compareAndSet(nextNode, node, false, false)) {
                return true;
            }
        }
    }

    public boolean remove(T value) {
        while (true) {
            Pair pair = find(value);
            Node currentNode = pair.currentNode;
            Node nextNode = pair.nextNode;
            if (nextNode == null || nextNode.value.compareTo(value) != 0) {
                return false;
            }

            Node next = nextNode.currentFlagAndNext.getReference();
            if (!nextNode.currentFlagAndNext.compareAndSet(next, next, false, true)) {
                continue;
            }
            // No need really delete right now
            currentNode.currentFlagAndNext.compareAndSet(nextNode, next, false, false);
            return true;
        }
    }


    public boolean contains(T value) {
        Node node = head.currentFlagAndNext.getReference();

        if (node == null) {
            return false;
        }
        while (node.currentFlagAndNext.getReference() != null && node.value.compareTo(value) < 0) {
            node = node.currentFlagAndNext.getReference();
        }
        return node.value.compareTo(value) == 0 && !node.currentFlagAndNext.isMarked();
    }


    public boolean isEmpty() {
        return head.currentFlagAndNext.getReference() == null;
    }

    private Pair find(T value) {
        while (true) {
            Node current = head;
            Node next = head.currentFlagAndNext.getReference();
            Node tmp;
            while (true) {
                if (next == null) {
                    return new Pair(current, null);
                }
                tmp = next.currentFlagAndNext.getReference();
                if (next.currentFlagAndNext.isMarked()) {
                    if (!current.currentFlagAndNext.compareAndSet(next, tmp, false, false)) {
                        break;
                    }
                    next = tmp;
                } else {
                    if (next.value.compareTo(value) >= 0) {
                        return new Pair(current, next);
                    }
                    current = next;
                    next = tmp;
                }
            }
        }
    }
}