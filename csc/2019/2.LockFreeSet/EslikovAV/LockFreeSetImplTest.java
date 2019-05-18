import org.junit.Assert;
import org.junit.Test;

public class LockFreeSetImplTest {

    @Test
    public void remove() {
        LockFreeSetImpl lockFreeSet = new LockFreeSetImpl<Integer>();
        Assert.assertTrue(lockFreeSet.isEmpty());
        Assert.assertFalse(lockFreeSet.remove(1));
        Assert.assertFalse(lockFreeSet.remove(2));

        lockFreeSet.add(1);
        Assert.assertFalse(lockFreeSet.remove(2));
        Assert.assertTrue(lockFreeSet.remove(1));

        lockFreeSet.add(1);
        lockFreeSet.add(2);
        Assert.assertTrue(lockFreeSet.remove(2));
        Assert.assertTrue(lockFreeSet.remove(1));

        Assert.assertTrue(lockFreeSet.isEmpty());

        for(int i =0; i < 10; i++){
            lockFreeSet.add(i);
        }

        Assert.assertTrue(lockFreeSet.remove(0));
        Assert.assertTrue(lockFreeSet.remove(9));
        Assert.assertTrue(lockFreeSet.remove(5));
    }

    @Test
    public void isEmpty() {
        LockFreeSetImpl lockFreeSet = new LockFreeSetImpl<Integer>();
        Assert.assertTrue(lockFreeSet.isEmpty());

        lockFreeSet.add(1);
        Assert.assertFalse(lockFreeSet.isEmpty());

        lockFreeSet.add(2);
        Assert.assertFalse(lockFreeSet.isEmpty());

        lockFreeSet.remove(1);
        Assert.assertFalse(lockFreeSet.isEmpty());

        lockFreeSet.remove(2);
        Assert.assertTrue(lockFreeSet.isEmpty());
    }

    @Test
    public void add() {
        LockFreeSetImpl lockFreeSet = new LockFreeSetImpl<Integer>();
        Assert.assertTrue(lockFreeSet.add(1));
        Assert.assertFalse(lockFreeSet.add(1));
    }

    @Test
    public void contains() {
        LockFreeSetImpl lockFreeSet = new LockFreeSetImpl<Integer>();
        Assert.assertFalse(lockFreeSet.contains(1));
        Assert.assertFalse(lockFreeSet.contains(2));

        lockFreeSet.add(1);
        Assert.assertTrue(lockFreeSet.contains(1));
        Assert.assertFalse(lockFreeSet.contains(2));

        lockFreeSet.add(2);
        Assert.assertTrue(lockFreeSet.contains(1));
        Assert.assertTrue(lockFreeSet.contains(2));
    }

    @Test
    public void bgigSet() {
        LockFreeSetImpl lockFreeSet = new LockFreeSetImpl<Integer>();
        for (int i = 0; i < 10_000; i++) {
            lockFreeSet.add(i);
        }
        for (int i = 0; i < 10_000; i++) {
            Assert.assertTrue(lockFreeSet.contains(i));
        }
    }

    @Test
    public void addInTheMiddle() {
        LockFreeSetImpl lockFreeSet = new LockFreeSetImpl<Integer>();
        Assert.assertTrue(lockFreeSet.add(1));
        Assert.assertTrue(lockFreeSet.add(10));
        Assert.assertTrue(lockFreeSet.add(5));
        Assert.assertTrue(lockFreeSet.add(15));
        Assert.assertTrue(lockFreeSet.add(7));
    }
}
