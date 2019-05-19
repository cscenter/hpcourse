import com.devexperts.dxlab.lincheck.LinChecker;
import com.devexperts.dxlab.lincheck.LoggingLevel;
import com.devexperts.dxlab.lincheck.Options;
import com.devexperts.dxlab.lincheck.annotations.Operation;
import com.devexperts.dxlab.lincheck.annotations.Param;
import com.devexperts.dxlab.lincheck.paramgen.IntGen;
import com.devexperts.dxlab.lincheck.strategy.stress.StressCTest;
import com.devexperts.dxlab.lincheck.strategy.stress.StressOptions;
import org.junit.Test;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

@Param(name = "val", gen = IntGen.class, conf = "1:10")
@StressCTest
public class LockFreeSetImplTest {

    private LockFreeSet<Integer> lfs = new LockFreeSetImpl<>();

    @Operation
    public boolean add(@Param(name = "val") int val) {
        return lfs.add(val);
    }

    @Operation
    public boolean remove(@Param(name = "val") int val) {
        return lfs.remove(val);
    }

    @Operation
    public boolean contains(@Param(name = "val") int val) {
        return lfs.contains(val);
    }

    @Operation
    public boolean isEmpty() {
        return lfs.isEmpty();
    }

    @Operation
    public Set<Integer> iterator() {
        Set<Integer> ans = new HashSet<>();
        for (Iterator<Integer> it = lfs.iterator(); it.hasNext(); )
            ans.add(it.next());
        return ans;
    }

    @Test
    public void runTest() {
        Options opts = new StressOptions()
                .iterations(50)
                .threads(3)
                .actorsPerThread(5)
                .logLevel(LoggingLevel.INFO);
        LinChecker.check(LockFreeSetImplTest.class, opts);
    }
}