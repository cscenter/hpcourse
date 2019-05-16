package org.sample;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class SnapCollectorImpl<T extends Comparable<T>> implements SnapCollector<T> {

    final int maxNThreads = 1024;
    final AtomicInteger counter = new AtomicInteger(-1);
    final ThreadLocal<Integer> index = new ThreadLocal<>();

    final DataNode<Report<T>> reportsListStopToken;
    final DataNode<SkipListNode<T>> nodesListStopToken;

    boolean active;
    ArrayList<DataNode<Report<T>>> reportsLists;
    ArrayList<DataNode<Report<T>>> reportsListsTails;

    DataNode<SkipListNode<T>> nodes;
    DataNode<SkipListNode<T>> tail;

    SnapCollectorImpl() {
        active = true;
        reportsLists = new ArrayList<>(maxNThreads);
        reportsListsTails = new ArrayList<>(maxNThreads);

        for (int i = 0; i < maxNThreads; i++) {
            DataNode<Report<T>> fakeReportNode = new DataNode<>(null, null);
            reportsLists.add(fakeReportNode);
            reportsListsTails.add(fakeReportNode);
        }

        DataNode<SkipListNode<T>> fakeNode = new DataNode<>(null, null);
        nodes = fakeNode;
        tail = fakeNode;

        reportsListStopToken = new DataNode<>(null, null);
        nodesListStopToken = new DataNode<>(null, null);
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public void deactivate() {
        active = false;
    }

    @Override
    public void addNode(SkipListNode<T> node) {
        retry:
        while (true) {
            if (active && tail != nodesListStopToken) {
                DataNode<SkipListNode<T>> realTail = tail;
                while (realTail.next.get() != null) {
                    realTail = realTail.next.get();
                }

                if ((realTail.data == null || node.value.compareTo(realTail.data.value) > 0) && realTail != nodesListStopToken) {
                    DataNode<SkipListNode<T>> newNode = new DataNode<>(node, null);
                    if (!realTail.next.compareAndSet(null, newNode))
                        continue retry;

                    tail = newNode;
                }

                return;

            } else
                return;
        }
    }

    @Override
    public ArrayList<SkipListNode<T>> readNodes() {
        ArrayList<SkipListNode<T>> data = new ArrayList<>();

        DataNode<SkipListNode<T>> currentNode = nodes.next.get();
        while(currentNode != nodesListStopToken) {
            data.add(currentNode.data);
            currentNode = currentNode.next.get();
        };

        return data;
    }

    @Override
    public void blockAddingNodes() {
        retry:
        while (true) {
            if (tail != nodesListStopToken) {
                DataNode<SkipListNode<T>> realTail = tail;
                while (realTail.next.get() != null) {
                    realTail = realTail.next.get();
                }

                if (realTail != nodesListStopToken) {
                    if (!realTail.next.compareAndSet(null, nodesListStopToken))
                        continue retry;

                    tail = nodesListStopToken;
                }
                return;

            } else
                return;
        }
    }

    @Override
    public void report(Report<T> report) {
        if (index.get() == null){
            int newIndex;
            do {
                newIndex = counter.get() + 1;
            } while(!counter.compareAndSet(newIndex-1, newIndex));
            index.set(newIndex);
        }

        int threadIndex = index.get();
        DataNode<Report<T>> threadReportsListTail = reportsListsTails.get(threadIndex);

        if (threadReportsListTail == reportsListStopToken || threadReportsListTail.next.get() != null || !active)
            return;

        DataNode<Report<T>> newReportNode = new DataNode<>(report, null);
        if (threadReportsListTail.next.compareAndSet(null, newReportNode))
            reportsListsTails.set(threadIndex, newReportNode);
    }

    @Override
    public ArrayList<Report<T>> readReports() {
        ArrayList<Report<T>> data = new ArrayList<>();

        for (int i=0; i<reportsLists.size(); ++i) {
            DataNode<Report<T>> currentNode = reportsLists.get(i).next.get();
            while (currentNode != reportsListStopToken) {
                data.add(currentNode.data);
                currentNode = currentNode.next.get();
            }
        }

        return data;
    }

    @Override
    public void blockReports() {

        for (int i =0; i<reportsLists.size(); ++i){
            retry:
            while(true){
                DataNode<Report<T>> realTail = reportsListsTails.get(i);
                while (realTail.next.get() != null)
                    realTail = realTail.next.get();

                if (realTail == reportsListStopToken)
                    break retry;

                if (!realTail.next.compareAndSet(null, reportsListStopToken))
                    continue  retry;

                reportsListsTails.set(i, reportsListStopToken);
                break retry;
            }
        }
    }
}

class SnapCollectorDummyImpl<T extends Comparable<T>> implements SnapCollector<T> {
    @Override
    public boolean isActive() {
        return false;
    }

    @Override
    public void deactivate() {

    }

    @Override
    public void addNode(SkipListNode<T> node) {

    }

    @Override
    public ArrayList<SkipListNode<T>> readNodes() {
        return null;
    }

    @Override
    public void blockAddingNodes() {

    }

    @Override
    public void report(Report report) {

    }

    @Override
    public ArrayList<Report<T>> readReports() {
        return null;
    }

    @Override
    public void blockReports() {

    }
}


class DataNode<T> {
    T data;
    AtomicReference<DataNode<T>> next;

    DataNode(T data, DataNode<T> next) {
        this.data = data;
        this.next = new AtomicReference<>(next);
    }
}
