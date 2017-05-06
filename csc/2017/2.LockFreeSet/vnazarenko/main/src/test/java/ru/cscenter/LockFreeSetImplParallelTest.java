package ru.cscenter;

import java.util.concurrent.ThreadLocalRandom;

import org.testng.annotations.*;

/**
 * Created by me on 19.04.17.
 */
public class LockFreeSetImplParallelTest {

    LockFreeSetImpl<Integer> set = null;

    @BeforeTest
    public void setUp() throws Exception {
        set = new LockFreeSetImpl<>();
    }

    @AfterTest
    public void tearDown() throws Exception {
        set = null;
    }

    @Test(threadPoolSize = 7, invocationCount = 10,  timeOut = 10000)
    public void mixedOperations() throws Exception {
        assert set != null;

        ThreadLocalRandom rand = ThreadLocalRandom.current();
        int[] vals = rand.ints(100).toArray();
        for (int i = 0; i < 1000000; i++) {
            int operationID = rand.nextInt(0, 4);
            int value = vals[rand.nextInt(0, vals.length)];
            switch (operationID) {
                case 0:
                    set.remove(value);
                    break;
                case 1:
                    set.add(value);
                    break;
                case 2:
                    set.contains(value);
                    break;
                case 3:
                    set.isEmpty();
                    break;
            }
        }
    }
}