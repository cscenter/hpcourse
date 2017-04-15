import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicMarkableReference;


public class LockFreeSetImpl<T extends Comparable<T>> implements LockFreeSet {
    private AtomicMarkableReference<Node> head = new AtomicMarkableReference<>(null, false);
    private AtomicInteger size = new AtomicInteger(0);

    private class Node {
        private AtomicMarkableReference<Node> next;
        private Comparable value;

        Node(final Comparable value, final AtomicMarkableReference<Node> next) {
            this.value = value;
            this.next = next;
        }
    }

    @Override
    public boolean add(Comparable value) {
        while (true) {
            // 1. If list is empty, set new head value
            if (head.getReference() == null) {
                Node newNode = new Node(value, new AtomicMarkableReference<>(null, false));

                if (head.compareAndSet(null, newNode, false, false)) {
                    size.incrementAndGet();
                    return true;
                } else {
                    continue;
                }
            }

            // 2. If list contains the value and it is not deleted, return false
            Node current = head.getReference();
            boolean currentIsMarked = head.isMarked();

            while (current != null) {
                if (value.compareTo(current.value) == 0 && !currentIsMarked) {
                    return false;
                }

                currentIsMarked = current.next.isMarked();
                current = current.next.getReference();
            }

            // 3. Otherwise set head value
            Node oldHead = head.getReference();
            boolean headIsMarked = head.isMarked();
            Node newNode = new Node(value, new AtomicMarkableReference<>(oldHead, headIsMarked));


            if (head.compareAndSet(oldHead, newNode, headIsMarked, false)) {
                size.incrementAndGet();
                return true;
            }
        }

    }

    @Override
    public boolean remove(Comparable value) {
        while (true) {
            // 1. If list is empty, we couldn't remove anything
            if (head.getReference() == null) {
                return false;
            }

            // 2. If not, we will try to find the value.
            // If we find it, mark it as read.
            AtomicMarkableReference<Node> current = head;
            Node currentReference = current.getReference();
            boolean currentIsMarked = current.isMarked();
            boolean casFailed = false;
            while (currentReference != null) {

                if (value.compareTo(currentReference.value) == 0 && !currentIsMarked) {
                    if (current.compareAndSet(currentReference, currentReference, false, true)) {
                        size.decrementAndGet();
                        return true;
                    } else {
                        casFailed = true;
                        // from first loop
                        continue;
                    }
                }
                current = current.getReference().next;
                currentReference = current.getReference();
                currentIsMarked = current.isMarked();
            }

            if (casFailed) {
                // from second loop
                continue;
            }

            // 3. if we fail to find the value, return false
            return false;
        }
    }

    @Override
    public boolean contains(Comparable value) {
        while (true) {
            // 1. If list is empty, it doesn't contain anything
            if (head.getReference() == null) {
                return false;
            }

            // 2. If not, we will try to find the value.
            // If we find it, then return true.
            // Let's clean up removed values during the search.
            AtomicMarkableReference<Node> current = head;
            Node previous = head.getReference();
            Node currentReference = current.getReference();
            boolean currentIsMarked = current.isMarked();

            while (currentReference != null) {
                if (value.compareTo(currentReference.value) == 0 && !currentIsMarked) {
                    return true;
                }

                if (currentIsMarked) {
                    boolean nextIsMarked = currentReference.next.isMarked();

                    if (currentReference.equals(head.getReference())) {
                        if (head.compareAndSet(currentReference, currentReference.next.getReference(), currentIsMarked, nextIsMarked)) {
                            previous = head.getReference();
                            current = head;
                        } else {
                            previous = current.getReference();
                            current = current.getReference().next;
                        }
                    } else {
                        if (previous.next.compareAndSet(currentReference, currentReference.next.getReference(), currentIsMarked, nextIsMarked)) {
                            current = previous.next;
                        } else {
                            previous = current.getReference();
                            current = current.getReference().next;
                        }
                    }
                } else {
                    previous = current.getReference();
                    current = current.getReference().next;
                }
                currentReference = current.getReference();
                currentIsMarked = current.isMarked();
            }

            // 3. if we fail to find the value, return false.
            return false;
        }
    }

    @Override
    public boolean isEmpty() {
        return (size.get() == 0);
    }
}
