import com.devexperts.dxlab.lincheck.LinChecker;
import com.devexperts.dxlab.lincheck.annotations.Operation;
import com.devexperts.dxlab.lincheck.strategy.stress.StressCTest;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

@StressCTest
public class LockFreeSetImplTest {
    private LockFreeSetImpl<Integer> set = new LockFreeSetImpl<>();
    @Operation
    public boolean add(int value) {
        return set.add(value);
    }

    @Operation
    public boolean contains(int value) {
        return set.contains(value);
    }

    @Operation
    public boolean remove(int value) {
        return set.remove(value);
    }

    @Operation
    public boolean isEmpty() {
        return set.isEmpty();
    }

    @Operation
    public List<Integer> iterator() {
        List<Integer> list = new ArrayList<>();
        for (Integer i: set) {
            list.add(i);
        }
        return list;
    }

    @Test
    public void test() {
        LinChecker.check(LockFreeSetImplTest.class);
    }
}
