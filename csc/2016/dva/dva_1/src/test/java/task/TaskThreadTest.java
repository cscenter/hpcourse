package task;

import communication.Protocol;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class TaskThreadTest {

    static AtomicInteger counter = new AtomicInteger(0);

    public static <TA, TB, TP, TM, TN> TaskThread BuildTask(TA a, TB b, TP p, TM m, TN n) {

        TaskParam ta, tb, tp, tm;
        ta = a instanceof TaskThread ? new TaskParam((TaskThread) a) : new TaskParam((Integer) a);
        tb = b instanceof TaskThread ? new TaskParam((TaskThread) b) : new TaskParam((Integer) b);
        tp = p instanceof TaskThread ? new TaskParam((TaskThread) p) : new TaskParam((Integer) p);
        tm = m instanceof TaskThread ? new TaskParam((TaskThread) m) : new TaskParam((Integer) m);
        return new TaskThread(counter.getAndIncrement(), ta, tb, tp, tm, (Integer) n);
    }

    @Test
    public void test1() {
        System.out.println();
        System.out.println();

        final TaskThread task1 = BuildTask(101, 102, 103, 1041, 205);
        final TaskThread task2 = BuildTask(1, 2, 1, 4, 0);
        final TaskThread task3 = BuildTask(task1, 12, 1, 100, 10);
        final TaskThread task4 = BuildTask(task1, 13, 1, 100, 10);
        final TaskThread task5 = BuildTask(task1, 14, 1, 100, 10);

        Thread printer =
        new Thread(() -> {
            System.out.println("result: " + task1.getResult());
            System.out.println("result: " + task2.getResult());
            System.out.println("result: " + task3.getResult());
            System.out.println("result: " + task4.getResult());
            System.out.println("result: " + task5.getResult());
        });
        printer.start();


        task3.start();
        task5.start();
        task1.start();
        task2.start();
        task4.start();

//        try {
//            printer.join();
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
    }
}