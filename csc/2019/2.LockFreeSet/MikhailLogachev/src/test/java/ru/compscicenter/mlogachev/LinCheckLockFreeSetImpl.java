package ru.compscicenter.mlogachev;

import com.devexperts.dxlab.lincheck.LinChecker;
import com.devexperts.dxlab.lincheck.annotations.Operation;
import com.devexperts.dxlab.lincheck.annotations.Param;
import com.devexperts.dxlab.lincheck.paramgen.IntGen;
import com.devexperts.dxlab.lincheck.strategy.stress.StressCTest;
import com.devexperts.dxlab.lincheck.strategy.stress.StressOptions;
import com.devexperts.dxlab.lincheck.verifier.linearizability.LinearizabilityVerifier;
import org.junit.Test;

@Param(name = "x", gen = IntGen.class, conf = "1:10")
@StressCTest(threads = 10)
public class LinCheckLockFreeSetImpl {

    private LockFreeSetImpl<Integer> lockFreeSet = new LockFreeSetImpl<>();

    @Operation
    public boolean add(@Param(name = "x") Integer x) { return lockFreeSet.add(x); }

    @Operation
    public boolean remove(@Param(name = "x") Integer x) { return lockFreeSet.remove(x); }

    @Operation
    public boolean contains(@Param(name = "x") Integer x) { return lockFreeSet.contains(x); }

    @Operation
    public boolean isEmpty(@Param(name = "x") Integer x) { return lockFreeSet.contains(x); }

    @Test
    public void runTest() {
        StressOptions opts = new StressOptions();
        opts.iterations(100);
        opts.threads(10);
        opts.actorsPerThread(10);
        opts.invocationsPerIteration(100);
        opts.verifier(LinearizabilityVerifier.class);

        LinChecker.check(LockFreeSetImpl.class, opts);
    }
}
