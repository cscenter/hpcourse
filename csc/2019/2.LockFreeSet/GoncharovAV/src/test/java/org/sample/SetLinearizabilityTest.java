package org.sample;

import com.devexperts.dxlab.lincheck.LinChecker;
import com.devexperts.dxlab.lincheck.annotations.Operation;
import com.devexperts.dxlab.lincheck.annotations.Param;
import com.devexperts.dxlab.lincheck.paramgen.IntGen;
import com.devexperts.dxlab.lincheck.strategy.stress.StressCTest;
import org.junit.Test;

@Param(name = "value", gen = IntGen.class, conf = "1:2")
@StressCTest(actorsBefore = 0, actorsAfter = 0, actorsPerThread = 3, threads = 3)
public class SetLinearizabilityTest {
    private LockFreeSetImpl<Integer> set = new LockFreeSetImpl<>();

    @Operation
    public boolean add(@Param(name = "value") int value) {
        return set.add(value);
    }

    @Operation
    public boolean contains(@Param(name = "value") int value) {
        return set.contains(value);
    }

    @Operation
    public boolean remove(@Param(name = "value") int value) {
        return set.remove(value);
    }

    @Operation
    public boolean isEmpty() {
        return set.isEmpty();
    }

    @Test
    public void test() {
        LinChecker.check(SetLinearizabilityTest.class);
    }
}
