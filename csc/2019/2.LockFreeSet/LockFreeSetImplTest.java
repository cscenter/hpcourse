import com.devexperts.dxlab.lincheck.LinChecker;
import com.devexperts.dxlab.lincheck.LoggingLevel;
import com.devexperts.dxlab.lincheck.Options;
import com.devexperts.dxlab.lincheck.annotations.OpGroupConfig;
import com.devexperts.dxlab.lincheck.annotations.Operation;
import com.devexperts.dxlab.lincheck.annotations.Param;
import com.devexperts.dxlab.lincheck.paramgen.IntGen;
import com.devexperts.dxlab.lincheck.paramgen.StringGen;
import com.devexperts.dxlab.lincheck.strategy.stress.StressCTest;
import com.devexperts.dxlab.lincheck.strategy.stress.StressOptions;
import com.devexperts.dxlab.lincheck.verifier.linearizability.LinearizabilityVerifier;

import java.util.*;

@Param(name = "key", gen = IntGen.class, conf = "1:3")
@StressCTest(verifier = LinearizabilityVerifier.class)
@OpGroupConfig(name = "T1", nonParallel = true)
@OpGroupConfig(name = "T2", nonParallel = true)
@OpGroupConfig(name = "T3", nonParallel = true)
public class LockFreeSetImplTest {

    private LockFreeSetImpl<Integer> set = new LockFreeSetImpl<>();

    public static void main(String[] args) {
        randomTests();
        lincheckTests();

//        LockFreeSetImpl<Integer> impl = new LockFreeSetImpl<>();
//        impl.add(1);
//        System.out.println(impl.iterator().hasNext());
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
//
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
//    @Operation
//    public int tryReadFirst() {
//        return tryReadFirst(set.iterator());
//    }

    private int tryReadFirst(Iterator<Integer> iterator){
        if (iterator.hasNext()){
            return iterator.next();
        } else {
            return -1;
        }
    }

    @Operation(runOnce = true, group="T1") public int trf(){ return tryReadFirst(set.iterator());}
    @Operation(runOnce = true, group="T2") public boolean a2_(){ return set.add(2);}
    @Operation(runOnce = true, group="T2") public int trf_(){ return tryReadFirst(set.iterator());}
    @Operation(runOnce = true, group="T3") public boolean a3(){ return set.add(3);}

    @org.junit.Test
    public static void lincheckTests() {
        Options opts = new StressOptions()
                .iterations(5000)
                .threads(3)
                .invocationsPerIteration(1000) // what does this parameter affect ?
                .actorsPerThread(2)
                .actorsBefore(0)
                .actorsAfter(0)
                .logLevel(LoggingLevel.INFO);
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