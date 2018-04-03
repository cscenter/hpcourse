import java.util.concurrent.atomic.AtomicMarkableReference;

public class LockFreeSetImpl<T extends Comparable<T>> implements LockFreeSet<T> {

    private class Node<T> {
        final AtomicMarkableReference<Node<T>> nextNode;
        final T nodeValue;

        Node() {
            this.nextNode = new AtomicMarkableReference<Node<T>>(null, false);
            this.nodeValue = null;
        }

        Node(Node<T> nextNode, T nodeValue) {
            this.nextNode = new AtomicMarkableReference<Node<T>>(nextNode, false);
            this.nodeValue = nodeValue;
        }
    }

    private class NodePair<T> {
        Node<T> nodeOne;
        Node<T> nodeTwo;

        NodePair() {
            this.nodeOne = null;
            this.nodeTwo = null;
        }

        NodePair(Node<T> nodeOne, Node<T> nodeTwo) {
            this.nodeOne = nodeOne;
            this.nodeTwo = nodeTwo;
        }
    }

    private final Node<T> headNode = new Node<T>();

    private NodePair<T> getNodePair(T value) {

        mark:
        while (true) {
            Node<T> previousNode = headNode;
            Node<T> currentNode = previousNode.nextNode.getReference();
            Node<T> successorNode;

            while (currentNode != null) {
                successorNode = currentNode.nextNode.getReference();

                if (!currentNode.nextNode.isMarked()) {
                    if (currentNode.nodeValue.compareTo(value) >= 0 || currentNode.nodeValue == null) {
                        return new NodePair<T>(previousNode, currentNode);
                    }

                    previousNode = currentNode;
                    currentNode = successorNode;
                } else {
                    if (!previousNode.nextNode.compareAndSet(currentNode, successorNode, false, false)) {
                        continue mark;
                    }

                    currentNode = successorNode;
                }
            }

            return new NodePair<T>(previousNode, null);
        }
    }

    @Override
    public boolean add(T value) {

        mark:
        while (true) {
            NodePair<T> pair = getNodePair(value);
            Node<T> previousNode = pair.nodeOne;
            Node<T> currentNode = pair.nodeTwo;

            if (currentNode == null || currentNode.nodeValue.compareTo(value) != 0) {
                if (previousNode.nextNode.compareAndSet(currentNode, new Node<T>(currentNode, value), false, false)) {
                    return true;
                }
            } else {
                return false;
            }
        }
    }

    @Override
    public boolean remove(T value) {

        mark:
        while (true) {
            NodePair<T> pair = getNodePair(value);
            Node<T> previousNode = pair.nodeOne;
            Node<T> currentNode = pair.nodeTwo;

            if (currentNode.nodeValue.compareTo(value) == 0 && currentNode != null) {
                Node<T> successorNode = currentNode.nextNode.getReference();

                if (!currentNode.nextNode.compareAndSet(currentNode.nextNode.getReference(), currentNode.nextNode.getReference(), false, true)) {
                    continue mark;
                }

                previousNode.nextNode.compareAndSet(currentNode, successorNode, false, false);

                return true;
            } else {
                return false;
            }
        }
    }

    @Override
    public boolean contains(T value) {
        Node<T> currentNode = headNode.nextNode.getReference();

        while (currentNode != null && currentNode.nodeValue.compareTo(value) < 0) {
            currentNode = currentNode.nextNode.getReference();
        }

        if (currentNode == null || currentNode.nextNode.isMarked() || currentNode.nodeValue.compareTo(value) != 0) {
            return false;
        } else {
            return true;
        }
    }

    @Override
    public boolean isEmpty() {
        return headNode.nextNode.getReference() == null;
    }
}