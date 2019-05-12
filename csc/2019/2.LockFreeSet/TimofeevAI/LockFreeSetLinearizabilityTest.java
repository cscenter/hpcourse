package da;

import com.devexperts.dxlab.lincheck.LinChecker;
import com.devexperts.dxlab.lincheck.LoggingLevel;
import com.devexperts.dxlab.lincheck.Options;
import com.devexperts.dxlab.lincheck.annotations.LogLevel;
import com.devexperts.dxlab.lincheck.annotations.Operation;
import com.devexperts.dxlab.lincheck.annotations.Param;
import com.devexperts.dxlab.lincheck.paramgen.IntGen;
import com.devexperts.dxlab.lincheck.strategy.stress.StressCTest;
import com.devexperts.dxlab.lincheck.strategy.stress.StressOptions;
import com.devexperts.dxlab.lincheck.verifier.linearizability.LinearizabilityVerifier;

import org.junit.Test;

@Param(name = "key", gen = IntGen.class, conf = "1:5")
@StressCTest(verifier = LinearizabilityVerifier.class)
@LogLevel(LoggingLevel.DEBUG)
public class LockFreeSetLinearizabilityTest {
    private LockFreeSetImpl<Integer> map = new LockFreeSetImpl<Integer>();;

    @Operation
    public boolean add(@Param(name = "key") int key) {
        return map.add(key);
    }

    @Operation
    public boolean remove(@Param(name = "key") int key) {
        return map.remove(key);
    }

    // @Operation
    // public boolean contains(@Param(name = "key") int key) {
    //     return map.contains(key);
    // }

    @Test
    public void test() {
        Options opts = new StressOptions()
            .iterations(5)
            .invocationsPerIteration(15)
            .threads(2)
            .logLevel(LoggingLevel.DEBUG);
        LinChecker.check(LockFreeSetLinearizabilityTest.class, opts);
    }
    
}