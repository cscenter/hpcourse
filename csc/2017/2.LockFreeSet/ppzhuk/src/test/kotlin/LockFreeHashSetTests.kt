import org.junit.Assert
import org.junit.Test
import java.util.concurrent.atomic.AtomicMarkableReference

class LockFreeHashSetTests {

    @Test
    fun testAdd() {
        val lockFreeSet = LockFreeHashSet<Int>()
        val res = lockFreeSet.add(123)
        val contains = lockFreeSet.contains(123)
        Assert.assertEquals(true, res)
        Assert.assertEquals(true, contains)
    }

    @Test
    fun testAddDuplicate() {
        val lockFreeSet = LockFreeHashSet<Int>()
        val res1 = lockFreeSet.add(123)
        val res2 = lockFreeSet.add(123)
        Assert.assertEquals(true, res1)
        Assert.assertEquals(false, res2)
    }

    @Test
    fun testContains() {
        val lockFreeSet = LockFreeHashSet<Int>()
        lockFreeSet.add(123)
        val contains = lockFreeSet.contains(123)
        Assert.assertEquals(true, contains)
    }

    @Test
    fun testRemove() {
        val lockFreeSet = LockFreeHashSet<Int>()
        lockFreeSet.add(123)
        val containsBefore = lockFreeSet.contains(123)
        val res = lockFreeSet.remove(123)
        val containsAfter = lockFreeSet.contains(123)
        Assert.assertEquals(true, containsBefore)
        Assert.assertEquals(true, res)
        Assert.assertEquals(false, containsAfter)
    }

    @Test
    fun testEmpty() {
        val lockFreeSet = LockFreeHashSet<Int>()
        Assert.assertEquals(true, lockFreeSet.isEmpty)
        lockFreeSet.add(123)
        lockFreeSet.add(124)
        Assert.assertEquals(false, lockFreeSet.isEmpty)
        lockFreeSet.remove(124)
        lockFreeSet.remove(123)
        Assert.assertEquals(true, lockFreeSet.isEmpty)
    }

    // !! before uncomment this make LockFreeHashSet.head and LockFreeHashSet.findNodes public
    // ----------------------------------------------------------------------------------------------------------------
    @Test
    fun testFindNodes() {
        val n4 = LockFreeHashSet<Int>().Node(4.hashCode(), 4, AtomicMarkableReference(null, false))
        val n10 = LockFreeHashSet<Int>().Node(10.hashCode(), 10, AtomicMarkableReference(null, false))
        val n20 = LockFreeHashSet<Int>().Node(20.hashCode(), 20, AtomicMarkableReference(null, false))
        val n30 = LockFreeHashSet<Int>().Node(30.hashCode(), 30, AtomicMarkableReference(null, false))

        n4.nextAndIsDeletePair.set(n10, false)
        n10.nextAndIsDeletePair.set(n20, false)
        n20.nextAndIsDeletePair.set(n30, false)

        val set = LockFreeHashSet<Int>()
        set.head.set(n4)

        checkWindow(set, 5, n4 to n10)
        checkWindow(set, 15, n10 to n20)
        checkWindow(set, 25, n20 to n30)
        checkWindow(set, 20, n10 to n20)
        checkWindow(set, 30, n20 to n30)
        checkWindow(set, 31, n30 to null)
        checkWindow(set, 2, null to n4)
    }

    @Test
    fun testFindNodesWithDeletedElements() {
        val n4 = LockFreeHashSet<Int>().Node(4.hashCode(), 4, AtomicMarkableReference(null, false))
        val n10 = LockFreeHashSet<Int>().Node(10.hashCode(), 10, AtomicMarkableReference(null, false))
        val n20 = LockFreeHashSet<Int>().Node(20.hashCode(), 20, AtomicMarkableReference(null, false))
        val n30 = LockFreeHashSet<Int>().Node(30.hashCode(), 30, AtomicMarkableReference(null, false))
        val n40 = LockFreeHashSet<Int>().Node(40.hashCode(), 40, AtomicMarkableReference(null, false))
        val n50 = LockFreeHashSet<Int>().Node(50.hashCode(), 50, AtomicMarkableReference(null, false))

        n4.nextAndIsDeletePair.set(n10, false)
        n10.nextAndIsDeletePair.set(n20, false)
        n20.nextAndIsDeletePair.set(n30, false)
        n30.nextAndIsDeletePair.set(n40, false)
        n40.nextAndIsDeletePair.set(n50, false)

        val set = LockFreeHashSet<Int>()
        set.head.set(n4)

        // first element is logically deleted
        n4.nextAndIsDeletePair.set(n10, true)

        checkWindow(set, 5, null to n10)
        checkWindow(set, 15, n10 to n20)
        checkWindow(set, 25, n20 to n30)
        checkWindow(set, 20, n10 to n20)
        checkWindow(set, 30, n20 to n30)
        checkWindow(set, 31, n30 to n40)
        checkWindow(set, 41, n40 to n50)
        checkWindow(set, 51, n50 to null)

        // last element is logically deleted
        n50.nextAndIsDeletePair.set(null, true)

        checkWindow(set, 5, null to n10)
        checkWindow(set, 15, n10 to n20)
        checkWindow(set, 25, n20 to n30)
        checkWindow(set, 20, n10 to n20)
        checkWindow(set, 30, n20 to n30)
        checkWindow(set, 31, n30 to n40)
        checkWindow(set, 41, n40 to null)
        checkWindow(set, 51, n40 to null)

        // two elements one after another are logically deleted
        n20.nextAndIsDeletePair.set(n30, true)
        n30.nextAndIsDeletePair.set(n40, true)

        checkWindow(set, 5, null to n10)
        checkWindow(set, 15, n10 to n40)
        checkWindow(set, 25, n10 to n40)
        checkWindow(set, 20, n10 to n40)
        checkWindow(set, 30, n10 to n40)
        checkWindow(set, 41, n40 to null)
        checkWindow(set, 51, n40 to null)
    }

    private fun checkWindow(
            set: LockFreeHashSet<Int>,
            key: Int,
            expectedNodes: Pair<LockFreeHashSet<Int>.Node?, LockFreeHashSet<Int>.Node?>
    ) {
        val actualNodes = set.findNodes(key)
        Assert.assertEquals(expectedNodes, actualNodes)
    }
    // ----------------------------------------------------------------------------------------------------------------
}