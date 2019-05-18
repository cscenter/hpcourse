import com.devexperts.dxlab.lincheck.LinChecker;
import com.devexperts.dxlab.lincheck.LoggingLevel;
import com.devexperts.dxlab.lincheck.Options;
import com.devexperts.dxlab.lincheck.annotations.Operation;
import com.devexperts.dxlab.lincheck.annotations.Param;
import com.devexperts.dxlab.lincheck.paramgen.IntGen;
import com.devexperts.dxlab.lincheck.strategy.stress.StressOptions;
import org.junit.Test;

@Param(name = "key", gen = IntGen.class)
public class LockFreeSetLinearizabilityTest {
    private LockFreeSetImpl<Integer> set = new LockFreeSetImpl<>();

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
    public void test() {
        Options opts = new StressOptions()
                .iterations(5)
                .threads(2)
                .logLevel(LoggingLevel.DEBUG);
        LinChecker.check(LockFreeSetLinearizabilityTest.class, opts);
    }
}