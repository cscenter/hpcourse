import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

/**
 * @author Ruslan Akhundov
 */
public class BlockingQueueImplTest {
    BlockingQueueImpl<Object> blockingQueue;

    @Before
    public void setUp() {
        blockingQueue = new BlockingQueueImpl<>(10);
    }

    @Test
    public void testPush_1() throws Exception {
        for (int i = 0; i < 10; i++) {
            blockingQueue.push(new Object());
        }
        assertEquals(10, blockingQueue.size());
    }

    @Test
    public void testPop() throws Exception {
        for (int i = 0; i < 10; i++) {
            blockingQueue.push(new Object());
        }
        assertEquals(10, blockingQueue.size());
        for (int i = 0; i < 10; i++) {
            blockingQueue.pop();
        }
        assertEquals(0, blockingQueue.size());
    }

    @Test
    public void testPushPop() throws Exception {
        Thread producer = new Thread(new Producer(100));
        Thread consumer = new Thread(new Consumer(100));
        producer.start();
        consumer.start();
        producer.join();
        consumer.join();
        assertEquals(0, blockingQueue.size());
    }

    @Test
    public void testPushBlock() throws Exception {
        Thread producer = new Thread(new Producer(11));
        producer.start();
        while (blockingQueue.size() != 10) Thread.yield();
        blockingQueue.pop();
        producer.join();
        assertEquals(10, blockingQueue.size());
    }

    @Test
    public void testPopBlock() throws Exception {
        Thread producer = new Thread(new Producer(10));
        producer.start();
        while(blockingQueue.size() != 10) Thread.yield();

        Thread consumer = new Thread(new Consumer(11));
        consumer.start();
        while (blockingQueue.size() != 0) Thread.yield();

        blockingQueue.push(new Object());
        producer.join();
        consumer.join();
        assertEquals(0, blockingQueue.size());
    }

    @Test
    public void stressTest() throws Exception {
        Thread[] producers = new Thread[10];
        Thread[] consumers = new Thread[10];
        for (int i = 0; i < producers.length; ++i) {
            producers[i] = new Thread(new Producer(10000));
            consumers[i] = new Thread(new Consumer(10000));
        }
        for (int i = 0; i < producers.length; ++i) {
            producers[i].start();
            consumers[i].start();
        }
        for (int i = producers.length - 1; i >= 0; --i) {
            consumers[i].join();
            producers[i].join();

        }
        assertEquals(0, blockingQueue.size());
    }

    private class Producer implements Runnable {
        public final int AMOUNT_TO_PUSH;

        public Producer(int toPush) {
            this.AMOUNT_TO_PUSH = toPush;
        }

        @Override
        public void run() {
            for (int i = 0; i < AMOUNT_TO_PUSH; i++) {
                try {
                    blockingQueue.push(new Object());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private class Consumer implements Runnable {
        public final int AMOUNT_TO_POP;

        public Consumer(int toPop) {
            this.AMOUNT_TO_POP = toPop;
        }

        @Override
        public void run() {
            for (int i = 0; i < AMOUNT_TO_POP; i++) {
                try {
                    blockingQueue.pop();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}