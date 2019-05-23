import com.devexperts.dxlab.lincheck.LinChecker
import com.devexperts.dxlab.lincheck.LoggingLevel.INFO
import com.devexperts.dxlab.lincheck.annotations.Operation
import com.devexperts.dxlab.lincheck.annotations.Param
import com.devexperts.dxlab.lincheck.paramgen.IntGen
import com.devexperts.dxlab.lincheck.strategy.stress.StressCTest
import com.devexperts.dxlab.lincheck.strategy.stress.StressOptions
import org.junit.Test


@Param(name = "data", gen = IntGen::class, conf = "1:10")
@StressCTest
class LockFreeSetTest {
    private val lockFreeSet = LockFreeSetImpl<Int>()

    val isEmpty: Boolean
        @Operation
        get() = lockFreeSet.isEmpty

    @Operation(params = ["data"])
    fun add(x: Int): Boolean {
        return lockFreeSet.add(x)
    }

    @Operation(params = ["data"])
    operator fun contains(x: Int): Boolean {
        return lockFreeSet.contains(x)
    }

    @Operation(params = ["data"])
    fun remove(x: Int): Boolean {
        return lockFreeSet.remove(x)
    }

    @Test
    fun test() {
        val opts = StressOptions()
            .iterations(25)
            .threads(NUM_THREADS)
            .actorsPerThread(5)
            .logLevel(INFO)
        LinChecker.check(LockFreeSetTest::class.java, opts)
    }
}
