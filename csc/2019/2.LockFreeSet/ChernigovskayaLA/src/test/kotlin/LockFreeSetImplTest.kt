import com.devexperts.dxlab.lincheck.LinChecker
import com.devexperts.dxlab.lincheck.LoggingLevel.INFO
import com.devexperts.dxlab.lincheck.annotations.Operation
import com.devexperts.dxlab.lincheck.annotations.Param
import com.devexperts.dxlab.lincheck.paramgen.IntGen
import com.devexperts.dxlab.lincheck.strategy.stress.StressCTest
import com.devexperts.dxlab.lincheck.strategy.stress.StressOptions
import org.junit.Test

@Param(name = "key", gen = IntGen::class, conf = "1:5")
@StressCTest
class LockFreeSetImplTest {
    private val set = LockFreeSetImpl<Int>()

    val isEmpty: Boolean
        @Operation
        get() = set.isEmpty()

    @Operation(params = ["key"])
    fun add(x: Int): Boolean {
        return set.add(x)
    }

    @Operation(params = ["key"])
    operator fun contains(x: Int): Boolean {
        return set.contains(x)
    }

    @Operation(params = ["key"])
    fun remove(x: Int): Boolean {
        return set.remove(x)
    }

    @Test
    fun test() {
        val opts = StressOptions()
            .iterations(100)
            .threads(3)
            .actorsPerThread(3)
        LinChecker.check(LockFreeSetImplTest::class.java, opts)
    }
}
