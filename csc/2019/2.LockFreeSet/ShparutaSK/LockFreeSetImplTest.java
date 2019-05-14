import com.devexperts.dxlab.lincheck.LinChecker;
import com.devexperts.dxlab.lincheck.LoggingLevel;
import com.devexperts.dxlab.lincheck.Options;
import com.devexperts.dxlab.lincheck.annotations.Operation;
import com.devexperts.dxlab.lincheck.annotations.Param;
import com.devexperts.dxlab.lincheck.paramgen.StringGen;
import com.devexperts.dxlab.lincheck.strategy.stress.StressCTest;
import com.devexperts.dxlab.lincheck.strategy.stress.StressOptions;
import com.devexperts.dxlab.lincheck.verifier.linearizability.LinearizabilityVerifier;
import org.junit.Test;

@Param(name = "value", gen = StringGen.class)
@StressCTest(verifier = LinearizabilityVerifier.class)
public class LockFreeSetImplTest {

    LockFreeSetImpl<String> set = new LockFreeSetImpl<String>();

    @Operation
    public boolean add(@Param(name = "value") String value){
        return set.add(value);
    }

    @Operation
    public boolean remove(@Param(name = "value") String value){
        return set.remove(value);
    }

    @Operation
    public boolean contains(@Param(name = "value") String value){
        return set.contains(value);
    }

    @Operation
    public boolean isEmpty() {
        return set.isEmpty();
    }

    @Test
    public void test()
    {
        Options opts = new StressOptions()
                .iterations(4)
                .threads(2)
                .logLevel(LoggingLevel.INFO);
        LinChecker.check(LockFreeSetImplTest.class, opts);
    }
}