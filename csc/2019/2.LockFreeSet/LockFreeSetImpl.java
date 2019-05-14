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
                    snapCollector.get().reportDelete(cur); // should it be there ?
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
        if (cur.item.equals(key)){
            if (cur.info.get().isPresented){
                snapCollector.get().reportInsert(cur);
                return true;
            } else {
                snapCollector.get().reportDelete(cur);
                return false;
            }
        } else {
            return false;
        }
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
                snapCollector.get().reportInsert(cur);
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
    private GrowingQueue<GrowingQueue<Report<ItemWrapper<T>>>> allThreadsReports = new GrowingQueue<>();
    private ThreadLocal<GrowingQueue<Report<ItemWrapper<T>>>> curThreadReports = new ThreadLocal<>();
    private GrowingQueue<ItemWrapper<T>> additions = new GrowingQueue<>();

    SnapCollector(boolean initActive){
        isActive=initActive;
    }

    private boolean setupThreadReports() {
        if (curThreadReports.get() == null){
            GrowingQueue<Report<ItemWrapper<T>>> reports = new GrowingQueue<>();
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
            curThreadReports.get().tryInsert(new Report<>(item, Report.ReportType.DELETE));
        }
    }

    public void reportInsert(ItemWrapper<T> item){
        if (isActive && setupThreadReports() && item.info.get().isPresented){
            curThreadReports.get().tryInsert(new Report<>(item, Report.ReportType.INSERT));
        }
    }

    private void blockReports(){
        // no new local queues can be added now since deactivate() was called, so we can iterate big queue
        allThreadsReports.freeze();
        Iterator<GrowingQueue<Report<ItemWrapper<T>>>> threadsIterator = allThreadsReports.iterate();
        while (threadsIterator.hasNext()){
            threadsIterator.next().freeze();
        }
    }

    private void blockAdditions(){
        additions.freeze();
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
                if (cur.item == null){ throw new RuntimeException("Null item is stored"); }
                reportFound(cur);
            }
            if (info.next!=null){
                cur=info.next;
            } else {
                break;
            }
        }
        // differs from the paper algorithm: do it outside loop since other threads may attempt to iterate before dummy insertion
        blockAdditions();
        deactivate(); // interrupts this loop in other collecting threads
        blockReports();

        List<ItemWrapper<T>> inserts = new ArrayList<>(), removals = new ArrayList<>();
        Iterator<GrowingQueue<Report<ItemWrapper<T>>>> threadsIterator = allThreadsReports.iterate();
        while (threadsIterator.hasNext()){
            GrowingQueue<Report<ItemWrapper<T>>> oneThreadReports = threadsIterator.next();
            Iterator<Report<ItemWrapper<T>>> reportIterator = oneThreadReports.iterate();
            while (reportIterator.hasNext()){
                Report<ItemWrapper<T>> report = reportIterator.next();
                (report.reportType == Report.ReportType.INSERT ? inserts : removals).add(report.item);
            }
        }

        Iterator<ItemWrapper<T>> addsIterator = additions.iterate();
        while (addsIterator.hasNext()){
            ItemWrapper<T> next = addsIterator.next();
            if (next == null) {
                System.err.println("trying to add item from null wrapper");
            }
            inserts.add(next);
        }

        // cannot use Set<T> directly since comparison should be done by reference, not value
        // otherwise there would be errros with same values being added and removed (only removal will count)
        List<ItemWrapper<T>> refs = new ArrayList<>();
        outer: for (ItemWrapper<T> ins: inserts){
            for (ItemWrapper<T> rm: removals){
                if (ins == rm){ // compare by reference!
                    continue outer;
                }
            }
            refs.add(ins);
        }
        Set<T> snapshot=refs.stream().map(it -> it.item).collect(Collectors.toSet());
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
    private static int idc = 0;
    private int id;
    private final ItemWrapper<T> dummy = new ItemWrapper<T>(null, new Info<T>(false, null));

    public GrowingQueue(){
        head = new ItemWrapper<T>(null, new Info<T>(true, null));
        tail = new AtomicReference<>(head);
        id = idc;
        idc++;
    }

    public boolean insert(T item){
        return insertWrapped(new ItemWrapper<T>(item, new Info<>(true, null)));
    }

    public boolean tryInsert(T item){
        return tryInsertWrapped(new ItemWrapper<T>(item, new Info<>(true, null)));
    }

    private boolean insertWrapped(ItemWrapper<T> item){
        while (true) {
            if (tryInsertWrapped(item)){
                return true;
            } else {
                if (tail.get() == dummy){
                    return false;
                }
            }
        }
    }

    private boolean tryInsertWrapped(ItemWrapper<T> newLast){
        ItemWrapper<T> curLast = tail.get();
        Info<T> curLastInfo = curLast.info.get();
        if (curLastInfo.next == null) {
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

    public void freeze(){
        insertWrapped(dummy);
        if (tail.get() != dummy) throw new RuntimeException("Dummy was not set");
    }

    // should be called only after freeze()
    public Iterator<T> iterate(){
        return new Iterator<T>() {
            ItemWrapper<T> ptr = head;
            @Override
            public boolean hasNext() {
                Info<T> curInfo = ptr.info.get();
                Info<T> nextInfo = curInfo.next.info.get();
                return nextInfo.isPresented; // only dummy has false
            }

            @Override
            public T next() {
                ptr = ptr.info.get().next;
                return ptr.item;
            }
        };
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
