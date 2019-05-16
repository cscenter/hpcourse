package ru.cscenter.dxahtepb.lockfreeset;

import com.devexperts.dxlab.lincheck.LinChecker;
import com.devexperts.dxlab.lincheck.annotations.Operation;
import com.devexperts.dxlab.lincheck.annotations.Param;
import com.devexperts.dxlab.lincheck.strategy.stress.StressCTest;
import com.devexperts.dxlab.lincheck.strategy.stress.StressOptions;
import com.devexperts.dxlab.lincheck.verifier.linearizability.LinearizabilityVerifier;
import org.junit.Test;

@StressCTest(threads = 12)
public class LockFreeSetImplLincheck {

  private LockFreeSetImpl<Integer> lockFreeSet = new LockFreeSetImpl<>();

  @Operation
  public boolean add(@Param(name = "x") Integer x) {
    return lockFreeSet.add(x);
  }

  @Operation
  public boolean remove(@Param(name = "x") Integer x) {
    return lockFreeSet.remove(x);
  }

  @Operation
  public boolean contains(@Param(name = "x") Integer x) {
    return lockFreeSet.contains(x);
  }

  @Operation
  public boolean isEmpty(@Param(name = "x") Integer x) {
    return lockFreeSet.contains(x);
  }

  @Test
  public void runTest() {
    LinChecker.check(LockFreeSetImpl.class, new StressOptions()
            .iterations(20000)
            .threads(12)
            .actorsPerThread(100)
            .invocationsPerIteration(200)
            .verifier(LinearizabilityVerifier.class));
  }
}
