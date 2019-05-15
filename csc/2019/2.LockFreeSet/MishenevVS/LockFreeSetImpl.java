import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicMarkableReference;
import java.util.concurrent.atomic.AtomicReference;

/**
 * I've used Harris realization.
 */

class EmptyException extends Exception {
}

enum ReportType {
    INSERTED, DELETED, DUMMY
}

class ReportNode {
    AtomicReference<ReportNode> next;

    ReportNode() {
        next = new AtomicReference<ReportNode>(null);
    }

    Object value;
    ReportType type = null;

    public ReportNode(Object value, ReportType type) {
        next = new AtomicReference<ReportNode>(null);
        this.value = value;
        this.type = type;
    }
}


class AscendingMichaelQueue<T extends Comparable<T>> {
    private class Node {
        private final T value;
        private final AtomicReference<Node> next;

        Node(T value, Node next) {
            this.value = value;
            this.next = new AtomicReference(next);
        }

        Node(T value) {
            this.value = value;
            this.next = new AtomicReference(null);
        }

    }

    AtomicReference<Node> head;//todo replace AtomicReference
    AtomicReference<Node> tail;

    public AscendingMichaelQueue() {

        Node sentinel = new Node(null, null);
        head = new AtomicReference<Node>(sentinel);
        tail = new AtomicReference<Node>(sentinel);
    }

    public List<T> getContent() {
        List<T> list = new LinkedList<T>();
        Node crnt = head.get();
        while (true) {
            crnt = crnt.next.get();
            if (crnt == null) break;
            if (crnt.value == null) break; // dummy node
            list.add(crnt.value);
        }
        return list;
    }

    public T enq(T value) {
        Node node = new Node(value, null);
        while (true) {
            Node last = tail.get();
            Node next = last.next.get();
            if (last == tail.get()) {
                if (next == null) {
                    // If the tail node holds a key greater than or equal to k, it
                    //doesn’t add the node and simply returns the tail node
                    if ((last.value == null && head.get() != last/*not sential */) || (last.value != null && value != null && last.value.compareTo(value) >= 0)) { // may be tail.get() instead last
                        return last.value;
                    }
                    if (last.next.compareAndSet(null, node)) {
                        tail.compareAndSet(last, node); //trying change tail
                        return value;
                    }
                } else {
                    if (tail.compareAndSet(last, next))  //trying change tail
                        return next.value;// return new tail
                }
            }
        }
    }

    public T deq() throws EmptyException {
        while (true) {
            Node first = head.get();
            Node last = tail.get();
            Node next = first.next.get();
            if (first == last) {
                if (next == null) {
                    throw new EmptyException();
                }
                tail.compareAndSet(last, next);
            } else {
                T value = next.value;
                if (head.compareAndSet(first, next))
                    return value;
            }
        }
    }


}

public class LockFreeSetImpl<T extends Comparable<T>> implements LockFreeSet<T> {


    private class Node implements Comparable<Node> {
        private final T value;
        private final AtomicMarkableReference<Node> next;

        Node(T value, Node next) {
            this.value = value;
            this.next = new AtomicMarkableReference(next, false);
        }

        Node getNext() {
            return next.getReference();
        }

        boolean isMarkedAsDeleted() {

            return next.isMarked();
        }

        public int compareTo(Node p) {

            return value.compareTo(p.value);
        }
    }


    private class CursorDTO {
        private final Node LeftNode;
        private final Node RightNode;

        CursorDTO(Node leftNode, Node rightNode) {
            LeftNode = leftNode;
            RightNode = rightNode;
        }
    }

    public class SnapCollector {
        final int MAX_THREADS = 10;
        private volatile boolean isActive = true;
        private ThreadLocal<Integer> dummyTid; // for indices array
        AtomicInteger counter;
        AscendingMichaelQueue<Node> nodes;


        ReportNode[] localListOfReports;
        String[] arr = new String[5];

        SnapCollector() {
            isActive = true;
            dummyTid = new ThreadLocal();

            counter = new AtomicInteger(0);
            nodes = new AscendingMichaelQueue<Node>();
            localListOfReports = new ReportNode[MAX_THREADS];
        }

        public void report(Node node, ReportType type) {
            if (dummyTid.get() == null) {
                dummyTid.set(counter.getAndIncrement());
                localListOfReports[dummyTid.get()] = new ReportNode();
            }
            ReportNode localNode = localListOfReports[dummyTid.get()];
            if (localNode == null) {
                System.err.println("local Node is null");
            }
            while (true) {
                ReportNode buf = localNode.next.get();
                if (buf == null) break;
                localNode = buf;
            }
            if (localNode.type == ReportType.DUMMY) return;
            if (localNode.type == ReportType.DUMMY) return;
            ReportNode newNode = new ReportNode(node, type);

            localNode.next.compareAndSet(null, newNode);
        }


        public Node addNode(Node v) { // we return tail of queue because of AddNode is wait free
            return nodes.enq(v);
        }

        public void BlockFurtherNodes() {
            // isBlock = true;
            nodes.enq(null); // null is Dummy T - than greater than any other T-type object
        }

        public void BlockFurtherReports() {
            for (int i = 0; i < MAX_THREADS; ++i) {
                ReportNode localNode = localListOfReports[i];
                if (localNode == null) continue; // even break;
                ReportNode newNode = new ReportNode(null, ReportType.DUMMY);
                localNode.next.compareAndSet(null, newNode);
            }

        }

        public void Deactivate() {
            isActive = false;
        }

        public boolean isActive() {
            return isActive;
        }

        public Set<Node> getReports(ReportType type) {
            Set<Node> reports = new HashSet<Node>();

            for (int i = 0; i < MAX_THREADS; ++i) {

                ReportNode crnNode = localListOfReports[i];
                if (crnNode == null) continue; // even break;
                while (true) {
                    crnNode = crnNode.next.get();
                    if (crnNode == null) break;
                    if (crnNode.value != null && crnNode.type == type) {
                        reports.add((Node) crnNode.value);
                    }
                }
            }
            return reports;
        }

        public Set<Node> getNodes() {
          /*  Set<Node> reports = new HashSet<Node>();

            try {
                while (true) {
                    Node node = nodes.deq();
                    if (node == null) {
                        break;
                    } else {
                        reports.add(node);
                    }
                }
            } catch (EmptyException e) {
                e.printStackTrace();
            }*/
            // queque is immutable after Blocking
            return new HashSet<Node>(nodes.getContent());
        }
    }

    private final Node head;

    // private final AtomicMarkableReference<SnapCollector> PSC;
    private final AtomicReference<SnapCollector> PSC;


    public LockFreeSetImpl() {
        head = new Node(null, null);
        //PSC = new AtomicMarkableReference(new SnapCollector(), false);
        PSC = new AtomicReference(new SnapCollector());
        PSC.get().Deactivate();
    }

    public boolean add(T value) {
        while (true) {
            CursorDTO cursor = search(value);
            Node currentNode = cursor.LeftNode;
            Node nextNode = cursor.RightNode;
            if (nextNode != null && nextNode.value.compareTo(value) == 0) {//  already exists
                ReportInsert(nextNode);
                return false;
            }
            Node node = new Node(value, nextNode);
            if (currentNode.next.compareAndSet(nextNode, node, false, false)) {
                ReportInsert(node);
                return true;
            }
        }
    }

    public boolean remove(T value) {
        while (true) {
            CursorDTO cursor = search(value);
            Node currentNode = cursor.LeftNode;
            Node nextNode = cursor.RightNode;
            if (nextNode == null || nextNode.value.compareTo(value) != 0) { // not found
                return false;
            }

            Node nextNext = nextNode.next.getReference();
            if (!nextNode.next.compareAndSet(nextNext, nextNext, false, true)) { //try to mark deleted, it's logical removing
                continue;
            }


            // optimization - try to really remove element right now
            ReportDelete(nextNode);
            currentNode.next.compareAndSet(nextNode, nextNext, false, false);
            return true;
        }
    }


    // it's wait-free
    public boolean contains(T key) {
        Node crnt = head.getNext();

        while (crnt != null && crnt.value.compareTo(key) < 0) {
            crnt = crnt.getNext();
        }
        if (crnt != null && crnt.value.compareTo(key) == 0) {// for report
            if (!crnt.next.isMarked()) ReportInsert(crnt);
            else ReportDelete(crnt);
        }

        return crnt != null && crnt.value.compareTo(key) == 0 && !crnt.next.isMarked();
    }

    public boolean isEmpty() {
        //= return head.getNext() == null;
        return !iterator().hasNext();
    }

    private CursorDTO search(T key) {

        while (true) {
            Node current = head;
            Node next = current.getNext();
            Node tmp;
            while (true) {
                if (next == null) { //it's tail
                    return new CursorDTO(current, null);
                }
                boolean[] isDeleted = {false};
                tmp = next.next.get(isDeleted);
                if (isDeleted[0]) { // next is removed
                    ReportDelete(next);
                    // help to another  thread
                    if (!current.next.compareAndSet(next, tmp, false, false)) { // try to replace references for really removing
                        break; //restart
                    }
                    next = tmp;
                } else {
                    if (next.value.compareTo(key) >= 0) { // next.key >= key
                        return new CursorDTO(current, next);
                    } // else go next
                    current = next;
                    next = tmp;
                }
            }
        }
    }

    private void ReportDelete(Node victim) {
        //boolean[] isActivate = {false};
        //SnapCollector snapCollector = PSC.get(isActivate);
        //if(isActivate[0] )
        SnapCollector snapCollector = PSC.get();
        if (snapCollector.isActive())
            snapCollector.report(victim, ReportType.DELETED);
    }

    private void ReportInsert(Node newNode) {
        SnapCollector snapCollector = PSC.get();
        if (snapCollector.isActive())
            snapCollector.report(newNode, ReportType.INSERTED);
    }

    public SnapCollector acquireSnapCollector() {
        while (true) {
            SnapCollector snapCollector = PSC.get();
            if (snapCollector.isActive()) {
                return snapCollector;
            }
            SnapCollector newSC = new SnapCollector();
            if (!PSC.compareAndSet(snapCollector, newSC))
                continue;
            else return newSC;
        }
    }

    public void collectSnapshot(SnapCollector snapCollector) {
        Node curr = head.getNext();
        if (snapCollector.isActive() && curr == null) {
            snapCollector.BlockFurtherNodes();
            snapCollector.Deactivate();
        }
        while (snapCollector.isActive()) {
            if (!curr.isMarkedAsDeleted())
                curr = snapCollector.addNode(curr);

            if (curr == null ) {// curr is the last
                snapCollector.BlockFurtherNodes();
                snapCollector.Deactivate();
                break;
            }
            curr = curr.getNext();
            if (curr == null ) {// curr is the last
                snapCollector.BlockFurtherNodes();
                snapCollector.Deactivate();
                break;
            }
        }
        snapCollector.BlockFurtherReports();
    }


    public List<T> reconstructUsingReports(SnapCollector snapCollector) {
        Set<Node> snapshot = new HashSet<Node>();
        Set<Node> inserted = snapCollector.getReports(ReportType.INSERTED);
        Set<Node> deleted = snapCollector.getReports(ReportType.DELETED);
        Set<Node> nodes = snapCollector.getNodes();
        snapshot.addAll(inserted);
        snapshot.addAll(nodes);
        snapshot.removeAll(deleted);
        List<T> list = new LinkedList<T>();
        for (Node n : snapshot) {
            list.add(n.value);
        }
        return list;
    }

    // it's wait-free because of some optimization
    public Iterator<T> iterator() {
        SnapCollector snapCollector = acquireSnapCollector();
        collectSnapshot(snapCollector);
        List<T> list = reconstructUsingReports(snapCollector);


        return list.iterator();
    }


}
