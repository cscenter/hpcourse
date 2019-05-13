import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

/**
 * Created by fresheed on 09.05.19.
 */
public class LockFreeSetImpl<T extends Comparable<T>> implements LockFreeSet<T> {
    final private ItemWrapper<T> root;
    private AtomicInteger insertsCounter = new AtomicInteger(0);
    final private AtomicReference<SnapCollector<T>> snapCollector;

    public LockFreeSetImpl(){
        root=new ItemWrapper<>(null, new Info<T>(true, null)); // root value doesn't matter since it is never used
        snapCollector=new AtomicReference<>(new SnapCollector<T>(false));
    }

    @Override
    public boolean add(T key) {
        retry: while (true) {
            Slot<T> slot = findSlot(key);
            // slot represents candidate position to insert valid at some point of time
            if (slot.second!=null && slot.second.item.equals(key)) { // already presented - skip
                snapCollector.get().reportInsert(slot.second);
                return false;
            } else {
                ItemWrapper newNode = new ItemWrapper(key, new Info<T>(true, slot.second));
                Info replacement = new Info<T>(true, newNode);
                boolean inserted = slot.first.CASinfo(true, slot.second, replacement);
                if (inserted) {
                    insertsCounter.incrementAndGet();
                    snapCollector.get().reportInsert(newNode);
                    return true;
                }
            }
        }
    }

    @Override
    public boolean remove(T key) {
        retry: while (true) {
            Slot<T> slot = findSlot(key);
            if (slot.second == null || !slot.second.item.equals(key)){
                //System.out.println("Cannot remove "+key);
                return false;
            } else {
                ItemWrapper<T> succ = slot.second.info.get().next;
                Info replacement = new Info<T>(false, succ);
                boolean result = slot.second.CASinfo(true, succ, replacement);
                //System.out.println("Removed "+key+"?: "+result);
                if (result) {
                    snapCollector.get().reportDelete(slot.second);
                    // also try to actually remove; not affects linearization
                    slot.first.CASinfo(true, slot.second, new Info<T>(true, succ));
                    return true;
                }
            }
        }
    }

    private Slot<T> findSlot(T key){
        // slot return is needed because if we'd return only prev,
        // there would be a problem of modifying prev.info.next and invalidating slot correctness
        ItemWrapper<T> prev, cur; // invariant: prev.info.next = cur at some point of time after findSlot call
        Info prevInfo;
        retry: while (true){ // outer repeat: do until success
            prev=root;
            prevInfo = prev.info.get();
            cur=prevInfo.next;
            while (cur!=null) { // inner repeat: list iteration
                Info<T> curInfo = cur.info.get();
                if (curInfo.isPresented){
                    if (cur.item.compareTo(key)>=0){
                        return new Slot<T>(prev, cur);
                    } else {
                        prev=cur;
                        prevInfo = curInfo;
                        cur=prevInfo.next;
                    }
                } else { // cur is marked for deletion; only proceed after actual deletion
                    Info<T> replacement = new Info<T>(true, curInfo.next);
                    boolean replaced=prev.CASinfo(true, cur, replacement);
                    if (replaced) {
                        cur = curInfo.next;
                    } else {
                        continue retry;
                    }
                }
            }
            return new Slot<T>(prev, cur);
        }
    }

    @Override
    public boolean contains(T key) {
        ItemWrapper<T> cur=root;
        Info info=cur.info.get();
        if (info.next==null) { // empty for sure
            return false;
        }
        cur=info.next;
        while (!cur.item.equals(key) && info.next!=null){
            cur=info.next;
            info=cur.info.get();
        }
        // ? an error was here: should re-read cur.info before return ?
        boolean found=cur.item.equals(key) && cur.info.get().isPresented;
        if (found){
            if (info.isPresented){
                snapCollector.get().reportInsert(cur);
            } else {
                snapCollector.get().reportDelete(cur);
            }
        }
        return found;
    }

    @Override
    public boolean isEmpty() {
        int insertsBefore = insertsCounter.get();
        ItemWrapper<T> cur=root;
        Info info=cur.info.get();
        if (info.next==null) { // empty for sure
            return true;
        }
        while (info.next!=null){
            cur=info.next;
            info=cur.info.get();
            if (info.isPresented) {
                return false;
            }
        }
        // at this point we haven't detected non-deleted nodes
        // if there were no inserts, then set is actually empty
        int insertsAfter = insertsCounter.get();
        return (insertsBefore == insertsAfter);
    }

    @Override
    public Iterator<T> iterator() {
        updateCollector();
        List<T> snapshot = snapCollector.get().collectSnapshot(root);
        return snapshot.iterator();
    }

    private void updateCollector() {
        SnapCollector collector = snapCollector.get();
        if (!collector.isActive()){
            snapCollector.compareAndSet(collector, new SnapCollector<T>(true));
        } // otherwise participate in current snapshot
    }

}

class ItemWrapper<T extends Comparable<T>> implements Comparable<ItemWrapper<T>>{
    final T item;
    final AtomicReference<Info> info;

    ItemWrapper(T item, Info info){
        this.item=item;
        this.info=new AtomicReference<>(info);
    }

    boolean CASinfo(boolean isPresented, ItemWrapper<T> next, Info replacement){
        // since Info fields are final, we can first non-atomically check them for desired values and then CAS if they match
        Info info=this.info.get();
        if (info == null || (info.isPresented==isPresented && info.next==next)){
            return this.info.compareAndSet(info, replacement);
        } else {
            return false;
        }
    }

    @Override
    public int compareTo(ItemWrapper<T> o) {
        return item.compareTo(o.item);
    }

    @Override
    public String toString(){
        return String.format("{%s, %s}", (item == null ? "N" : item).toString(), (info == null ? "N" : info.get()).toString());
    }
}

class Info<T extends Comparable<T>> {
    final boolean isPresented;
    final ItemWrapper<T> next;

    Info(boolean isPresented, ItemWrapper<T> next){
        this.isPresented=isPresented;
        this.next=next;
    }

    @Override
    public String toString(){
        return String.format("(%b, --> %s)", isPresented, (next == null ? "X" : next.toString()));
    }
}


class Slot<T extends Comparable<T>> {
    final ItemWrapper<T> first, second; // guarantee that first.next=second

    Slot(ItemWrapper<T> first, ItemWrapper<T> second){
        this.first=first;
        this.second=second;
    }
}

class Report<T extends Comparable<T>> implements Comparable<Report<T>> {
    @Override
    public int compareTo(Report<T> o) {
        return item.compareTo(o.item);
    }

    enum ReportType {
        INSERT, DELETE
    }
    public final T item;
    public final ReportType reportType;

    Report(T item, ReportType reportType){
        this.item=item;
        this.reportType=reportType;
    }

    @Override
    public String toString(){
        return String.format("(%s, %s)", (reportType==ReportType.INSERT ? "I": "D"),
                (item == null ? "X" : item).toString());
    }
}

class SnapCollector<T extends Comparable<T>> {

    // volatile since some threads may try to report after deactivate() and don't see isActive's new value
    volatile private boolean isActive;
    private GrowingQueue<GrowingQueue<Report<T>>> allThreadsReports = new GrowingQueue<>(null);
    private ThreadLocal<GrowingQueue<Report<T>>> curThreadReports = new ThreadLocal<>();
    private GrowingQueue<ItemWrapper<T>> additions = new GrowingQueue<>(
            new ItemWrapper<T>(null, new Info(false, null)));

    private final Report<T> DUMMY = new Report<T>(null, null);

    SnapCollector(boolean initActive){
        isActive=initActive;
    }

    private boolean setupThreadReports() {
        if (curThreadReports.get() == null){
            GrowingQueue<Report<T>> reports = new GrowingQueue<>(null);
            boolean inserted = allThreadsReports.insert(reports); // lock-free, NOT wait-free
            if (inserted) {
                curThreadReports.set(reports);
                return true;
            } else {
                return false;
            }
        } else {
            return true;
        }
    }

    public void reportDelete(ItemWrapper<T> item){
        if (isActive && setupThreadReports()){
            try {curThreadReports.get().tryInsert(new Report<T>(item.item, Report.ReportType.DELETE));}
            catch (RuntimeException e) {}
        }
    }

    public void reportInsert(ItemWrapper<T> item){
        if (isActive && setupThreadReports()){
            try {curThreadReports.get().tryInsert(new Report<T>(item.item, Report.ReportType.INSERT));}
            catch (RuntimeException e) {}
        }
    }

    private void blockReports(){
        // no new local queues can be added now since deactivate() was called, so we can iterate big queue
        ItemWrapper<GrowingQueue<Report<T>>> queue = allThreadsReports.head;
        Info<GrowingQueue<Report<T>>> info = queue.info.get();
        while (info.next != null) {
            queue = info.next;
            info = queue.info.get();
            try {queue.item.tryInsert(queue.item.dummy);}
            catch (RuntimeException e) {}
        }
    }

    private void blockAdditions(){
        additions.insert(additions.dummy);
    }

    public void reportFound(ItemWrapper<T> item){
        if (isActive){
            additions.insert(item);
        }
    }
//
    public void deactivate(){
        isActive = false;
    }

    public boolean isActive(){
        return isActive;
    }

    public List<T> collectSnapshot(ItemWrapper<T> root){
        ItemWrapper<T> cur=root;
        while (isActive){
            Info<T> info = cur.info.get();
            if (info.isPresented && cur!=root){
                reportFound(cur);
            }
            if (info.next!=null){
                cur=info.next;
            } else {
                deactivate(); // interrupts this loop in other collecting threads
                blockAdditions();
            }
        }
        blockReports();

        Set<T> inserts = new HashSet<T>(), removals = new HashSet<T>();
        ItemWrapper<GrowingQueue<Report<T>>> oneThreadReports = allThreadsReports.head;
        Info<GrowingQueue<Report<T>>> threadReportsInfo = oneThreadReports.info.get();
        //while (threadReportsInfo != null && threadReportsInfo.next.item != null) {
        while (oneThreadReports.item != allThreadsReports.dummy) {
            oneThreadReports = threadReportsInfo.next;
            threadReportsInfo = oneThreadReports.info.get();
            ItemWrapper<Report<T>> report = oneThreadReports.item.head;
            Info<Report<T>> reportInfo = report.info.get();
            //while (reportInfo.next != null && reportInfo.next.item != null) {
            while (report.item != oneThreadReports.item.dummy) {
                report = reportInfo.next;
                reportInfo = report.info.get();
                (report.item.reportType == Report.ReportType.INSERT ? inserts : removals).add(report.item.item);
            }
        }

        ItemWrapper<ItemWrapper<T>> foundNode = additions.head;
//        Info<ItemWrapper<T>> nodeInfo = foundNode.info.get();
        //while (nodeInfo.next != null && nodeInfo.next.item != null) {
        while (foundNode.item != additions.dummy) {
//            nodeInfo = foundNode.info.get();
            if (foundNode != additions.head) {inserts.add(foundNode.item.item); }
            foundNode = foundNode.info.get().next;
        }

        Set<T> snapshot=new HashSet<>();
        snapshot.addAll(inserts);
        snapshot.removeAll(removals);

        List<T> listSnapshot=new ArrayList<T>(snapshot);
        Collections.sort(listSnapshot);
        return listSnapshot;
    }
}

/* Implements a lock-free interface for insertions only
 */
class GrowingQueue<T extends Comparable<T>> implements Comparable<GrowingQueue<T>> {
    public final ItemWrapper<T> head;
    public AtomicReference<ItemWrapper<T>> tail;
    public final T dummy;
    private static int idc = 0;
    private int id;

    public GrowingQueue(T dummyItem){
        head = new ItemWrapper<T>(null, new Info<T>(true, null));
        tail = new AtomicReference<>(head);
        dummy = dummyItem;
        id = idc;
        idc++;
    }

    public boolean insert(T item){
        while (true) {
            try {
                if (tryInsert(item)){
                    break;
                }
            } catch (RuntimeException e){ // don't try if dummy was detected
                return false;
            }
        }
        return true;
    }

    public boolean tryInsert(T item){
        ItemWrapper<T> curLast = tail.get();
        if (curLast == dummy) throw new RuntimeException("dummy inserted");
        Info<T> curLastInfo = curLast.info.get();
        if (curLastInfo.next == null) {
            ItemWrapper<T> newLast = new ItemWrapper<T>(item, new Info<>(true, null));
            Info<T> replacement = new Info<T>(true, newLast);
            boolean added = curLast.CASinfo(true, null, replacement);
            if (added){
                boolean fixed = tail.compareAndSet(curLast, newLast);
                return true;
            } else {
                return false;
            }
        } else {
            Info<T> replacement = new Info<T>(true, curLastInfo.next);
            tail.compareAndSet(curLast, curLastInfo.next);
            return false;
        }
    }

    // needed to organize queue of queues
    @Override
    public int compareTo(GrowingQueue<T> o) {
        return new Integer(id).compareTo(new Integer(o.id));
    }

    @Override
    public String toString(){
        return String.format("Q%d", id);
    }
}
