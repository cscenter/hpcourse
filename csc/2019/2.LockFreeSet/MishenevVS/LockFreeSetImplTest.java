import com.devexperts.dxlab.lincheck.LinChecker;
import com.devexperts.dxlab.lincheck.annotations.Operation;
import com.devexperts.dxlab.lincheck.annotations.Param;
import com.devexperts.dxlab.lincheck.paramgen.IntGen;
import com.devexperts.dxlab.lincheck.strategy.stress.StressCTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Admin
 */
@Param(name = "key", gen = IntGen.class)//, conf = "1:15"

@StressCTest(iterations = 300, threads = 4)
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

    @Test
    public void testLinear() {
         LinChecker.check(LockFreeSetImplTest.class);

    }

    @Test
    public void testMy() {

        LockFreeSetImpl<Integer> lfsInt = new LockFreeSetImpl();
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