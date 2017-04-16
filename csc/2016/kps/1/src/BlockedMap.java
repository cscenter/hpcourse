package first;


import java.util.Set;
import java.util.Map;
import java.util.HashSet;
import java.util.HashMap;


class BlockedMap<A, B> {
    
    private Map<A, B> map;
    
    public BlockedMap() {
        map = new HashMap<A, B>();
    }
    
    synchronized public B get(A a) {
        return map.get(a);
    }
    
    synchronized public void put(A a, B b) {
        map.put(a, b);
    }
    
    public Set<A> keySetIterable() {
        // Множество должно быть неизменяемым во время
        // итерирования, поэтому делается копия
        Set<A> res = new HashSet<A>();
        synchronized (this) {
            for (A a : map.keySet()) {
                res.add(a);
            }
        }
        
        return res;
    }
}