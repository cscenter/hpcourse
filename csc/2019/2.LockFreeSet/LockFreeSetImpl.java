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

    public LockFreeSetImpl(){
        root=new ItemWrapper<>(null, new Info<T>(true, null)); // root value doesn't matter since it is never used
    }

    @Override
    public boolean add(T key) {
        retry: while (true) {
            Slot<T> slot = findSlot(key);
            // slot represents candidate position to insert valid at some point of time
            if (slot.second!=null && slot.second.item.equals(key)) { // already presented - skip
                return false;
            } else {
                ItemWrapper newNode = new ItemWrapper(key, new Info<T>(true, slot.second));
                Info replacement = new Info<T>(true, newNode);
                boolean inserted = slot.first.CASinfo(true, slot.second, replacement);
                if (inserted) {
                    insertsCounter.incrementAndGet();
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
        throw new RuntimeException("debug");
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
        return String.format("{%s, %s}", (item == null ? "N" : item).toString(), info.get().toString());
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

