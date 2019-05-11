import com.devexperts.dxlab.lincheck.LinChecker;
import com.devexperts.dxlab.lincheck.LoggingLevel;
import com.devexperts.dxlab.lincheck.Options;
import com.devexperts.dxlab.lincheck.annotations.Operation;
import com.devexperts.dxlab.lincheck.annotations.Param;
import com.devexperts.dxlab.lincheck.paramgen.StringGen;
import com.devexperts.dxlab.lincheck.strategy.stress.StressCTest;
import com.devexperts.dxlab.lincheck.strategy.stress.StressOptions;
import com.devexperts.dxlab.lincheck.verifier.linearizability.LinearizabilityVerifier;

import java.util.*;

@Param(name = "key", gen = StringGen.class, conf = "1:5")
@StressCTest(verifier = LinearizabilityVerifier.class)
public class LockFreeSetImplTest {

    private LockFreeSetImpl<String> set = new LockFreeSetImpl<>();

    public static void main(String[] args) {
        randomTests();
        lincheckTests();
    }


    @Operation
    public boolean add(@Param(name = "key") String key) {
        return set.add(key);
    }

    @Operation
    public boolean remove(@Param(name = "key") String key) {
        return set.remove(key);
    }

    @org.junit.Test
    public static void lincheckTests() {
        Options opts = new StressOptions()
                .iterations(50)
                .threads(4)
                .invocationsPerIteration(50) // what does it affect? still 5 operations are shown in log
                .logLevel(LoggingLevel.INFO);
        //opts.
        LinChecker.check(LockFreeSetImplTest.class, opts);
    }

    private static void randomTests() {
        for (int repeats = 0; repeats < 10000; repeats++) {
            Set<Integer> etalon = new HashSet<>();
            LockFreeSet<Integer> impl = new LockFreeSetImpl<>();
            List<String> log = new ArrayList<>();
            for (int operation = 0; operation < 500; operation++) {
                int op = new Random().nextInt(4);
                int value = new Random().nextInt(5);
                switch (op) {
                    case 0:
                        log.add("Add " + value);
                        if (etalon.add(value) != impl.add(value)) throw new RuntimeException(log.toString());
                        break;
                    case 1:
                       log.add("Remove " + value);
                        if (etalon.remove(value) != impl.remove(value)) throw new RuntimeException(log.toString());
                        break;
                    case 2:
                        log.add("Check " + value);
                        if (etalon.contains(value) != impl.contains(value)) throw new RuntimeException(log.toString());
                        break;
                    case 3:
                        log.add("Iterator");
                        Set<Integer> etalonContents = new HashSet<>(), implContents = new HashSet<>();
                        etalon.iterator().forEachRemaining(etalonContents::add);
                        impl.iterator().forEachRemaining(implContents::add);
                        if (!etalonContents.equals(implContents)) throw new RuntimeException(log.toString());
                        break;
                }
            }
        }
        System.out.println("Random testing succeeded");
    }
}