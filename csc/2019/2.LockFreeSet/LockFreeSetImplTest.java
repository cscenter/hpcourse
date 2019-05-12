import com.devexperts.dxlab.lincheck.LinChecker;
import com.devexperts.dxlab.lincheck.LoggingLevel;
import com.devexperts.dxlab.lincheck.Options;
import com.devexperts.dxlab.lincheck.annotations.Operation;
import com.devexperts.dxlab.lincheck.annotations.Param;
import com.devexperts.dxlab.lincheck.paramgen.IntGen;
import com.devexperts.dxlab.lincheck.paramgen.StringGen;
import com.devexperts.dxlab.lincheck.strategy.stress.StressCTest;
import com.devexperts.dxlab.lincheck.strategy.stress.StressOptions;
import com.devexperts.dxlab.lincheck.verifier.linearizability.LinearizabilityVerifier;

import java.util.*;

@Param(name = "key", gen = IntGen.class, conf = "1:2") // what does conf affect ?
@StressCTest(verifier = LinearizabilityVerifier.class)
public class LockFreeSetImplTest {

    private LockFreeSetImpl<Integer> set = new LockFreeSetImpl<>();

    public static void main(String[] args) {
        randomTests();
        lincheckTests();
    }

//    @Operation
//    public boolean add(@Param(name = "key") int key) {
//        return set.add(key);
//    }
//
//    @Operation
//    public boolean remove(@Param(name = "key") int key) {
//        return set.remove(key);
//    }

    @Operation(runOnce = true)
    public String A2R2C2(){
        boolean r1 = set.add(2), r2 = set.remove(2), r3 = set.contains(2);
        return String.format("%d%d%d", (r1?1:0), (r2?1:0), (r3?1:0));
    }

    @Operation(runOnce = true)
    public String A1R1(){
        boolean r1 = set.add(1), r2 = set.remove(1);
        return String.format("%d%d", (r1?1:0), (r2?1:0));
//        return "T";
    }

    /*@Operation
    public  tryReadSecond() {
        return set.remove(key);
    }
*/
//    @Operation
//    public boolean contains(@Param(name = "key") int key) {
//        return set.contains(key);
//    }
//
//    @Operation
//    public boolean isEmpty() {
//        return set.isEmpty();
//    }
//
    @org.junit.Test
    public static void lincheckTests() {
        Options opts = new StressOptions()
                .iterations(30000)
                .threads(2)
                .invocationsPerIteration(10) // what does this parameter affect ?
                .actorsPerThread(1)
                .actorsBefore(0)
                .actorsAfter(0)
                .logLevel(LoggingLevel.WARN);
        LinChecker.check(LockFreeSetImplTest.class, opts);
    }

    private static void randomTests() {
        for (int repeats = 0; repeats < 10000; repeats++) {
            Set<Integer> etalon = new HashSet<>();
            LockFreeSet<Integer> impl = new LockFreeSetImpl<>();
            List<String> log = new ArrayList<>();
            for (int operation = 0; operation < 50; operation++) {
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