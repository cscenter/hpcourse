package org.sample;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicMarkableReference;
import java.util.concurrent.atomic.AtomicReference;

public class LockFreeSetImpl<T extends Comparable<T>> implements LockFreeSet<T> {

    static final int maxHeight = 32;

    private final SkipListNode<T> head;
    private final SkipListNode<T> tail;

    private AtomicReference<SnapCollector<T>> snapCollector;

    public LockFreeSetImpl() {
        head = new SkipListNode<>(null, maxHeight);
        tail = new SkipListNode<>(null, maxHeight);

        for (int i = 0; i < maxHeight; ++i) {
            head.next.get(i).compareAndSet(null, tail, false, false);
        }

        snapCollector = new AtomicReference<>(new SnapCollectorDummyImpl<>());
    }

    @Override
    public boolean add(T value) {
        retry:
        while (true) {
            Bounds<T> search_result = search(value, false);
            SkipListNode<T> closest_node = search_result.rights.get(0);

            if (closest_node != tail && closest_node.value.compareTo(value) == 0) {
                int height = closest_node.next.size();
                if (closest_node.next.get(0).isMarked()) {
                    continue retry;
                } else {
                    snapCollector.get().report(new Report<>(closest_node, Operation.INSERT));
                    return false;
                }
            }

            SkipListNode<T> new_node = SkipListNode.make(value);
            for (int i = 0; i < new_node.next.size(); i++)
                new_node.next.get(i).compareAndSet(null, search_result.rights.get(i), false, false);

            if (!search_result.lefts.get(0).next.get(0).compareAndSet(search_result.rights.get(0), new_node, false, false))
                continue retry;

            snapCollector.get().report(new Report<>(new_node, Operation.INSERT));

            for (int i = 1; i < new_node.next.size(); ++i) {
                while (true) {
                    if (new_node.next.get(i).isMarked())
                        break;

                    if (search_result.lefts.get(i).next.get(i).compareAndSet(search_result.rights.get(i), new_node, false, false))
                        break;

                    search_result = search(value, false);
                }
            }

            search(value, false);
            return true;
        }
    }

    @Override
    public boolean remove(T value) {
        Bounds<T> search_result = search(value, false);
        SkipListNode<T> closest_node = search_result.rights.get(0);

        if (closest_node == tail || closest_node.value.compareTo(value) != 0)
            return false;

        boolean marked_by_current_thread = mark(closest_node);
        search(value, false);

        return marked_by_current_thread;
    }

    @Override
    public boolean contains(T value) {
        Bounds<T> search_result = search(value, true);

        SkipListNode<T> closest_node = search_result.rights.get(0);
        if (closest_node != tail && closest_node.value.compareTo(value) == 0) {
            snapCollector.get().report(new Report<>(closest_node, Operation.INSERT));
            return true;
        }
        else
            return false;
    }

    @Override
    public boolean isEmpty() {

        int nNodes = 0;
        for(Iterator<T> i = iterator(); i.hasNext(); i.next())
            nNodes++;

        return nNodes == 0;
    }

    @Override
    public Iterator<T> iterator() {
        SnapCollector<T> localSnapCollector = snapCollector.get();

        if (!localSnapCollector.isActive()){
            snapCollector.compareAndSet(localSnapCollector, new SnapCollectorImpl<>());
        }

        localSnapCollector = snapCollector.get();

        if (localSnapCollector.isActive()) {
            SkipListNode<T> currentNode = head.next.get(0).getReference();
            while(currentNode != tail && localSnapCollector.isActive()){
                if (!currentNode.next.get(0).isMarked())
                    localSnapCollector.addNode(currentNode);
                currentNode = currentNode.next.get(0).getReference();
            }

            localSnapCollector.blockAddingNodes();
            localSnapCollector.deactivate();
        }
        localSnapCollector.blockReports();

        ArrayList<SkipListNode<T>> nodes = localSnapCollector.readNodes();
        ArrayList<Report<T>> reports = localSnapCollector.readReports();

        HashSet<SkipListNode<T>> deletedNodes = new HashSet<>();
        for (Report<T> report: reports){
            if (report.op == Operation.DELETE)
                deletedNodes.add(report.node);
            else
                nodes.add(report.node);
        }
        HashSet<SkipListNode<T>> uniqueNodes = new HashSet<>(nodes);

        ArrayList<T> result = new ArrayList<>();
        for (SkipListNode<T> node : uniqueNodes){
            if (!deletedNodes.contains(node))
                result.add(node.value);
        }

        Collections.sort(result);

        return new IteratorImpl(result.iterator());
    }

    private Bounds<T> search(T value, boolean ignoreFailedRemovals) {
        retry:
        while (true) {
            SkipListNode<T> left = head;

            ArrayList<SkipListNode<T>> lefts = new ArrayList<>();
            ArrayList<SkipListNode<T>> rights = new ArrayList<>();

            for (int i = maxHeight - 1; i >= 0; i--) {
                if (left.next.get(i).isMarked() && !ignoreFailedRemovals)
                    continue retry;

                SkipListNode<T> left_next = left.next.get(i).getReference();
                SkipListNode<T> right = left_next;
                SkipListNode<T> right_next = right.next.get(i).getReference();
                while (right.next.get(i).isMarked()) {
                    right = right_next;
                    right_next = right.next.get(i).getReference();
                }

                while (right != tail && right.value.compareTo(value) < 0) {

                    left = right;
                    left_next = right_next;
                    right = right_next;
                    right_next = right.next.get(i).getReference();
                    while (right.next.get(i).isMarked()) {
                        right = right_next;
                        right_next = right.next.get(i).getReference();
                    }
                }

                if (left_next != right) {

                    if (i == 0){
                        SkipListNode<T> currentMarkedNode = left_next;
                        while (currentMarkedNode != right) {
                            snapCollector.get().report(new Report<>(currentMarkedNode, Operation.DELETE));
                            currentMarkedNode = currentMarkedNode.next.get(0).getReference();
                        }
                    }

                    boolean removeMarkedSuccess = left.next.get(i).compareAndSet(left_next, right, false, false);
                    if (!removeMarkedSuccess && !ignoreFailedRemovals)
                        continue retry;
                }

                lefts.add(left);
                rights.add(right);

            }

            Collections.reverse(lefts);
            Collections.reverse(rights);

            return new Bounds<>(lefts, rights);
        }
    }

    private boolean mark(SkipListNode<T> node) {
        boolean marked_by_current_thread = true;
        for (int i = node.next.size() - 1; i >= 0; i--) {
            boolean mark_success;
            do {
                if (node.next.get(i).isMarked()) {
                    marked_by_current_thread = false;
                    break;
                } else
                    marked_by_current_thread = true;

                SkipListNode<T> node_next = node.next.get(i).getReference();
                mark_success = node.next.get(i).compareAndSet(node_next, node_next, false, true);
            } while (!mark_success);
        }
        return marked_by_current_thread;
    }

    class IteratorImpl implements Iterator<T>{
        Iterator<T> otherIterator;

        IteratorImpl(Iterator<T> otherIterator){
            this.otherIterator = otherIterator;
        }

        @Override
        public boolean hasNext() {
            return otherIterator.hasNext();
        }

        @Override
        public T next() {
            return otherIterator.next();
        }
    }

}


class SkipListNode<T extends Comparable<T>> {

    T value;
    ArrayList<AtomicMarkableReference<SkipListNode<T>>> next;

    static <T2 extends Comparable<T2>> SkipListNode<T2> make(T2 value) {
        long number =  1 + (long) (Math.random() * (1L << (LockFreeSetImpl.maxHeight-1)));
        int height = leastSignificantBitPosition(number);

        return new SkipListNode<>(value, height);
    }

    static int leastSignificantBitPosition(long number) {
        number &= -number;

        int pos = 0;
        while (number != 0) {
            number >>= 1;
            pos++;
        }

        return pos;
    }

    SkipListNode(T value, int height) {
        this.value = value;

        next = new ArrayList<>(height);
        for (int i = 0; i < height; i++) {
            next.add(new AtomicMarkableReference<>(null, false));
        }
    }
}


class Bounds<T extends Comparable<T>> {
    final ArrayList<SkipListNode<T>> lefts;
    final ArrayList<SkipListNode<T>> rights;

    Bounds(ArrayList<SkipListNode<T>> lefts, ArrayList<SkipListNode<T>> rights) {
        this.lefts = lefts;
        this.rights = rights;
    }
}