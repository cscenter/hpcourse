import org.junit.Assert
import org.junit.Test
import java.util.concurrent.atomic.AtomicMarkableReference

class LockFreeHashSetTests {

    @Test
    fun testAdd() {
        val lockFreeSet = LockFreeHashSet<Int>()
        val res = lockFreeSet.add(123)
        Assert.assertEquals(true, res)
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
    fun testAddAfterLast() {
        val lockFreeSet = LockFreeHashSet<Int>()
        val res1 = lockFreeSet.add(123)
        val res2 = lockFreeSet.add(124)
        val res3 = lockFreeSet.add(125)
        Assert.assertEquals(true, res1)
        Assert.assertEquals(true, res2)
        Assert.assertEquals(true, res3)
    }

    @Test
    fun testAddBeforeFirst() {
        val lockFreeSet = LockFreeHashSet<Int>()
        val res1 = lockFreeSet.add(125)
        val res2 = lockFreeSet.add(124)
        val res3 = lockFreeSet.add(123)
        Assert.assertEquals(true, res1)
        Assert.assertEquals(true, res2)
        Assert.assertEquals(true, res3)
    }

    @Test
    fun testAddBetweenTwoElementsDuplicate() {
        val lockFreeSet = LockFreeHashSet<Int>()
        val res1 = lockFreeSet.add(120)
        val res2 = lockFreeSet.add(130)
        val res3 = lockFreeSet.add(130)
        Assert.assertEquals(true, res1)
        Assert.assertEquals(true, res2)
        Assert.assertEquals(false, res3)
    }

    @Test
    fun testAddBetweenTwoElements() {
        val lockFreeSet = LockFreeHashSet<Int>()
        val res1 = lockFreeSet.add(120)
        val res2 = lockFreeSet.add(130)
        val res3 = lockFreeSet.add(125)
        Assert.assertEquals(true, res1)
        Assert.assertEquals(true, res2)
        Assert.assertEquals(true, res3)
    }

    @Test
    fun testRemoveEmptyList() {
        val lockFreeSet = LockFreeHashSet<Int>()
        val res = lockFreeSet.remove(123)
        Assert.assertEquals(false, res)
    }

    @Test
    fun testRemoveLast() {
        val lockFreeSet = LockFreeHashSet<Int>()
        lockFreeSet.add(123)
        val res = lockFreeSet.remove(123)
        Assert.assertEquals(true, res)
        Assert.assertEquals(true, lockFreeSet.isEmpty)
    }

    @Test
    fun testRemoveGreaterThenLast() {
        val lockFreeSet = LockFreeHashSet<Int>()
        lockFreeSet.add(123)
        val res = lockFreeSet.remove(124)
        Assert.assertEquals(false, res)
        Assert.assertEquals(false, lockFreeSet.isEmpty)
    }

    @Test
    fun testRemove() {
        val lockFreeSet = LockFreeHashSet<Int>()
        lockFreeSet.add(123)
        lockFreeSet.add(124)
        lockFreeSet.add(125)
        val res = lockFreeSet.remove(124)
        Assert.assertEquals(true, res)
        Assert.assertEquals(false, lockFreeSet.contains(124))
    }

    @Test
    fun testContains() {
        val lockFreeSet = LockFreeHashSet<Int>()
        lockFreeSet.add(123)
        val contains = lockFreeSet.contains(123)
        Assert.assertEquals(true, contains)
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

    @Test
    fun testConcurrentAdd() {
        (0..10000).forEach { iteration ->
            val lockFreeSet = LockFreeHashSet<Int>()
            // Added values in different threads are intersected a little bit
            val t1 = Thread(
                    Runnable {
                        lockFreeSet.add(10)
                        lockFreeSet.add(20)
                        lockFreeSet.add(25)
                    }
            )
            val t2 = Thread(
                    Runnable {
                        lockFreeSet.add(10)
                        lockFreeSet.add(3)
                        lockFreeSet.add(2)
                        lockFreeSet.add(1)
                    }
            )
            val t3 = Thread(
                    Runnable {
                        lockFreeSet.add(3)
                        lockFreeSet.add(5)
                        lockFreeSet.add(15)
                        lockFreeSet.add(25)
                    }
            )
            t1.run()
            t2.run()
            t3.run()
            t1.join()
            t2.join()
            t3.join()

            Assert.assertEquals(
                    "iteration: ${iteration}",
                    "1->2->3->5->10->15->20->25->null\n",
                    lockFreeSet.toString()
            )
        }
    }

    @Test
    fun testConcurrentRemove() {
        (0..10000).forEach { iteration ->
            val lockFreeSet = LockFreeHashSet<Int>()
            (1..10).forEach {
                lockFreeSet.add(it)
            }
            // Deleted values in different threads are intersected a little bit
            val t1 = Thread(
                    Runnable {
                        (2..5).forEach { lockFreeSet.remove(it) }
                    }
            )
            val t2 = Thread(
                    Runnable {
                        (5..8).forEach { lockFreeSet.remove(it) }
                    }
            )
            val t3 = Thread(
                    Runnable {
                        (6..9).forEach { lockFreeSet.remove(it) }
                    }
            )
            t3.run()
            t1.run()
            t2.run()
            t1.join()
            t2.join()
            t3.join()

            Assert.assertEquals(
                    "iteration: ${iteration}",
                    "1->10->null\n",
                    lockFreeSet.toString()
            )
        }
    }

    @Test
    fun testConcurrentAddRemove() {
        (0..10000).forEach { iteration ->
            val lockFreeSet = LockFreeHashSet<Int>()
            lockFreeSet.add(1)
            lockFreeSet.add(3)
            lockFreeSet.add(5)
            lockFreeSet.add(7)
            lockFreeSet.add(8)

            val t1 = Thread(
                    Runnable {
                        lockFreeSet.add(6)
                        lockFreeSet.add(2)
                        lockFreeSet.add(4)
                    }
            )
            val t2 = Thread(
                    Runnable {
                        lockFreeSet.remove(7)
                        lockFreeSet.remove(3)
                        lockFreeSet.remove(5)
                    }
            )
            t1.run()
            t2.run()
            t1.join()
            t2.join()

            Assert.assertEquals(
                    "iteration: ${iteration}",
                    "1->2->4->6->8->null\n",
                    lockFreeSet.toString()
            )
        }
    }

    // some tests for the private LockFreeHashSet.findNodes() method are places here
    // before uncomment this make LockFreeHashSet.head, LockFreeHashSet.findNodes and LockFreeHashSet.Node public
    // ----------------------------------------------------------------------------------------------------------------
    /*@Test
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
    }*/
    // ----------------------------------------------------------------------------------------------------------------
}