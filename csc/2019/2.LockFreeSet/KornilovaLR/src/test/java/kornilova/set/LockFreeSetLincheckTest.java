package kornilova.set;

import com.devexperts.dxlab.lincheck.LinChecker;
import com.devexperts.dxlab.lincheck.annotations.Operation;
import com.devexperts.dxlab.lincheck.strategy.stress.StressCTest;
import org.junit.Test;

@StressCTest(iterations = 100)
public class LockFreeSetLincheckTest {
    private LockFreeSet<Integer> q = new LockFreeSetImpl<>();

    @Operation
    public boolean add(Integer x) {
        return q.add(x);
    }

    @Operation
    public boolean remove(Integer x) {
        return q.remove(x);
    }

    @Operation
    public boolean contains(Integer x) {
        return q.contains(x);
    }

    @Operation
    public boolean isEmpty() {
        return q.isEmpty();
    }

    @Operation
    public int sum() {
        int sum = 0;
        for (Integer i : q) {
            sum += i;
        }
        return sum;
    }

    @Test
    public void runTest() {
        long l = System.currentTimeMillis();
        LinChecker.check(LockFreeSetLincheckTest.class);
        System.out.println(System.currentTimeMillis() - l);
    }
}
