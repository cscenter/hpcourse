import com.devexperts.dxlab.lincheck.LinChecker;
import com.devexperts.dxlab.lincheck.annotations.Operation;
import com.devexperts.dxlab.lincheck.annotations.Param;
import com.devexperts.dxlab.lincheck.paramgen.IntGen;
import com.devexperts.dxlab.lincheck.strategy.stress.StressCTest;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Admin
 */
@Param(name = "key", gen = IntGen.class)//, conf = "1:15"

/// Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 121.111 sec
@StressCTest(iterations = 100, threads = 3)
public class LockFreeSetImplTest {
    LockFreeSetImpl<Integer> set = new LockFreeSetImpl();

    @Operation
    public Boolean put(@Param(name = "key") int key) {
        return set.add(key);
    }

    @Operation
    public Boolean delete(@Param(name = "key") int key) {
        return set.remove(key);
    }

     @Operation
    public Boolean contains(@Param(name = "key") int key) {
        return set.contains(key);
    }

   @Operation
    public Boolean isEmpty() {
        return set.isEmpty();
    }

    @Operation
    public Set<Integer> iterator() {
        Set<Integer> result = new HashSet();
        Iterator<Integer> it = set.iterator();
        while ( it.hasNext() ) {
            result.add(it.next());
        }
        return result;
    }

    @Test
    public void testLinear() {
         LinChecker.check(LockFreeSetImplTest.class);

    }
    // it test for my checking of inner realization
    @Test
    public void testManualMode() {
    //manual mode
        LockFreeSetImpl<Integer> lfsInt = new LockFreeSetImpl();
        lfsInt.add(1);
        lfsInt.add(2);
        LockFreeSetImpl.SnapCollector snapCollector = lfsInt.acquireSnapCollector();

        lfsInt.add(3);
        lfsInt.add(4);
        lfsInt.remove(1);
        lfsInt.collectSnapshot(snapCollector);
        List<Integer> list = lfsInt.reconstructUsingReports(snapCollector);
        System.out.println(list.toString());
    }
    @Test
    public void testMy() {

        LockFreeSetImpl<Integer> lfsInt = new LockFreeSetImpl();
        assertEquals(lfsInt.iterator().hasNext(), false);
        lfsInt.add(1);
        lfsInt.add(2);
        lfsInt.add(3);

        assertEquals(lfsInt.iterator().hasNext(), true);

        LockFreeSetImpl<String> lfs = new LockFreeSetImpl();
        lfs.add(new String("one"));
        lfs.add(new String("two"));
        lfs.add(new String("three"));
        assertEquals(lfs.add(new String("one")), false);
        assertEquals(lfs.isEmpty(), false);

        lfs.remove(new String("one"));

        assertEquals(lfs.contains("two"), true);
        lfs.remove(new String("two"));
        assertEquals(lfs.contains("two"), false);

        assertEquals(lfs.remove(new String("three")), true);
        //assertEquals(lfs.isEmpty(), true);
        assertEquals(lfs.remove(new String("three")), false);
        assertEquals(lfs.isEmpty(), true);
    }

}