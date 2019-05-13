package org.sample.lincheck_tests;

import com.devexperts.dxlab.lincheck.LinChecker;
import com.devexperts.dxlab.lincheck.annotations.Operation;
import com.devexperts.dxlab.lincheck.annotations.Param;
import com.devexperts.dxlab.lincheck.paramgen.IntGen;
import com.devexperts.dxlab.lincheck.strategy.stress.StressCTest;
import com.devexperts.dxlab.lincheck.verifier.serializability.SerializabilityVerifier;
import org.junit.Test;
import org.sample.LockFreeSetImpl;

import java.util.concurrent.ConcurrentHashMap;

import java.util.HashMap;

@Param(name = "value", gen = IntGen.class, conf = "1:10")
@StressCTest(actorsBefore = 5, actorsAfter = 5, actorsPerThread = 5)
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
