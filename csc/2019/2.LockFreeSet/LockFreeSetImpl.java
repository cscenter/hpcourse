import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Created by fresheed on 09.05.19.
 */
public class LockFreeSetImpl<T extends Comparable<T>> implements LockFreeSet<T> {
    final private ItemWrapper<T> root;
    final private AtomicReference<SnapCollector<T>> snapCollector;

    public LockFreeSetImpl(){
        root=new ItemWrapper<>(null, new Info<T>(true, null)); // root value doesn't matter since it is never used
        snapCollector=new AtomicReference<>(new SnapCollector<T>());
    }

    @Override
    public boolean add(T key) {
        retry: while (true) {
            Slot<T> slot = findSlot(key);
            // slot represents candidate position to insert valid at some point of time
            if (slot.second!=null && slot.second.item.equals(key)) { // already presented - skip
                snapCollector.get().reportInsert(slot.second);
                //System.out.println("reported 1");
                return false;
            } else {
                ItemWrapper inserted = new ItemWrapper(key, new Info<T>(true, slot.second));
                Info replacement = new Info<T>(true, inserted);
                boolean result = slot.first.CASinfo(true, slot.second, replacement);
                if (result) {
                    snapCollector.get().reportInsert(inserted);
                    //System.out.println("reported 2 " + inserted);
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
                // ? report in case of marked note ?
                return false;
            } else {
                ItemWrapper<T> succ = slot.second.info.get().next;
                Info replacement = new Info<T>(false, succ);
                boolean result = slot.second.CASinfo(true, succ, replacement);
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
                if (cur.info.get().isPresented){
                    if (cur.item.compareTo(key)>=0){
                        return new Slot<T>(prev, cur);
                    } else {
                        prev=cur;
                        prevInfo = prev.info.get();
                        cur=prevInfo.next;
                    }
                } else { // cur is marked for deletion; only proceed after actual deletion
                    Info<T> replacement = new Info<T>(true, cur.info.get().next);
                    boolean replaced=prev.CASinfo(true, cur, replacement);
                    if (replaced) {
                        cur = prev.info.get().next;
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
        boolean found=cur.item.equals(key) && info.isPresented;
        if (found){
            if (cur.info.get().isPresented){
                snapCollector.get().reportInsert(cur);
                //System.out.println("reported 3");
            } else {
                snapCollector.get().reportDelete(cur);
            }
        }
        return found;
    }

    @Override
    public boolean isEmpty() {
        return iterator().hasNext();
    }

    @Override
    public Iterator<T> iterator() {
        updateCollector();
        List<T> snapshot = snapCollector.get().collectSnapshot(root);
        return snapshot.iterator();
    }

    private void updateCollector() {
        if (!snapCollector.get().isActive()){
            snapCollector.compareAndSet(snapCollector.get(), new SnapCollector<T>());
        } // otherwise participate in current snapshot
    }

//    @Override
//    public String toString(){
//        ItemWrapper<T> cur=root;
//        Info info=cur.info.get();
//        if (info.)
//        StringBuffer buf=new StringBuffer("");
//        while (cur!=null){
//            buf.append("-> "+info.next.toString());
//
//        }
//    }
}

class ItemWrapper<T extends Comparable<T>> {
    T item;
    AtomicReference<Info> info;

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

}

class Info<T extends Comparable<T>> {
    final boolean isPresented;
    final ItemWrapper<T> next;

    Info(boolean isPresented, ItemWrapper<T> next){
        this.isPresented=isPresented;
        this.next=next;
    }
}


class Slot<T extends Comparable<T>> {
    final ItemWrapper<T> first, second; // guarantee that first.next=second

    Slot(ItemWrapper<T> first, ItemWrapper<T> second){
        this.first=first;
        this.second=second;
    }
}

/* Every time thread gathers insert/delete -related data, he reports it in order to keep snapshot being built consistent
 */
class SnapCollector<T extends Comparable<T>> {

    private boolean isActive = true;
    private final Set<ItemWrapper<T>> reportedInserts = new HashSet(), reportedRemovals = new HashSet<>(),
            reportedFounds = new HashSet<>();

    public void reportDelete(ItemWrapper<T> item){
        if (isActive){
            reportedRemovals.add(item);
        }
    }

    public void reportInsert(ItemWrapper<T> item){
        if (isActive){
            reportedInserts.add(item);
        }
    }

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
                reportedFounds.add(cur);
            }
            if (info.next!=null){
                cur=info.next;
            } else {
                deactivate(); // interrupts this loop in other collecting threads
            }
        }

        Set<ItemWrapper<T>> snapshot=new HashSet<>();
        //System.out.println("Init: " + snapshot);
        snapshot.addAll(reportedInserts);
        //System.out.println("After inserts: " + snapshot);
        snapshot.addAll(reportedFounds);
        //System.out.println("After found: " + snapshot);
        snapshot.removeAll(reportedRemovals);
        //System.out.println("After removals: " + snapshot);
        List<T> listSnapshot=snapshot.stream().map(it -> it.item).collect(Collectors.toList());
        Collections.sort(listSnapshot);
        return listSnapshot;
    }

}
