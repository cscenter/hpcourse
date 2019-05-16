package org.sample;

import java.util.ArrayList;

enum Operation {
    INSERT,
    DELETE
}

class Report<T extends Comparable<T>> {
    SkipListNode<T> node;
    Operation op;

    Report(SkipListNode<T> node, Operation op) {
        this.node = node;
        this.op = op;
    }
}

interface SnapCollector<T extends Comparable<T>> {
    boolean isActive();

    void Deactivate();

    void addNode(SkipListNode<T> node);

    ArrayList<SkipListNode<T>> readNodes();

    void blockAddingNodes();

    void report(Report<T> report);

    ArrayList<Report<T>> readReports();

    void blockReports();
}
