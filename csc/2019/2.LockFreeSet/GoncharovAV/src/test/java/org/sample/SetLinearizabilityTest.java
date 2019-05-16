package org.sample;

import com.devexperts.dxlab.lincheck.LinChecker;
import com.devexperts.dxlab.lincheck.annotations.Operation;
import com.devexperts.dxlab.lincheck.annotations.Param;
import com.devexperts.dxlab.lincheck.paramgen.IntGen;
import com.devexperts.dxlab.lincheck.strategy.stress.StressCTest;
import org.junit.Test;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Iterator;

@Param(name = "value", gen = IntGen.class, conf = "1:10")
@StressCTest(actorsBefore = 2, actorsAfter = 2, actorsPerThread = 4, threads = 2)
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

    @Operation
    public ArrayList<Integer> iterate(){

        ArrayList<Integer> list = new ArrayList<>();
        for (Iterator<Integer> i = set.iterator(); i.hasNext();)
            list.add(i.next());

        return list;
    }

    @Test
    public void test() {
        LinChecker.check(SetLinearizabilityTest.class);
    }
}
