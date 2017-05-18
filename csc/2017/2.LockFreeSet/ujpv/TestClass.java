import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

class TaskAdd implements Runnable {
    TaskAdd(LockFreeSet<String> list, int size) {
        this.set = list;
        this.size = size;
    }

    private LockFreeSet<String> set;
    private int size;

    @Override
    public void run() {
        for (int i = 0; i < size; ++i) {
            set.add("" + i);
        }
    }
}

class TaskClean implements Runnable {
    TaskClean(LockFreeSet<String> list, int size) {
        this.set = list;
        this.size = size;
    }

    private LockFreeSet<String> set;
    private int size;

    @Override
    public void run() {
        for (int i = 0; i < size; ++i) {
            set.remove("" + i);
        }
    }
}

class TaskAddRandom implements Runnable {
    TaskAddRandom(LockFreeSet<String> list, int size) {
        this.set = list;
        this.size = size;
    }

    private LockFreeSet<String> set;
    private int size;

    @Override
    public void run() {
        for (int i = 0; i < size; ++i) {
            set.add("" + (ThreadLocalRandom.current().nextInt(-size, size)));
        }
    }
}

class TaskRemoveRandom implements Runnable {
    TaskRemoveRandom(LockFreeSet<String> list, int size) {
        this.set = list;
        this.size = size;
    }

    private LockFreeSet<String> set;
    private int size;

    @Override
    public void run() {
        for (int i = 0; i < size; ++i) {
            set.remove("" + (ThreadLocalRandom.current().nextInt(-size, size)));
        }
    }
}

public class TestClass {
    private static final int THREAD_AMOUNT = 10;
    private static final int SIZE = 10000;

    public static void main(String[] args) {
        LockFreeSet<String> list = new LockFreeSetImpl<>();

        List<Thread> threads = new ArrayList<>();

        System.out.println("Starting TaskAdd");
        for (int i = 0; i < THREAD_AMOUNT; ++i) {
            Thread e = new Thread(new TaskAdd(list, SIZE));
            threads.add(e);
            e.start();
        }

        System.out.println("Join TaskAdd");
        for (int i = 0; i < THREAD_AMOUNT; ++i) {
            try {
                threads.get(i).join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        if (list.isEmpty()) {
            System.out.println("Set shouldn't be empty");
        }

        System.out.println("Check contains");
        for (int i = 0; i < SIZE; ++i) {
            if (!list.contains((new Integer(i)).toString()))
                System.out.println("Set should contain: " + i);
        }

        for (int i = SIZE + 1; i < (SIZE + 1) * 2; ++i) {
            if (list.contains((new Integer(i)).toString()))
                System.out.println("Set shouldn't contain: " + i);
        }

        System.out.println("Starting TaskClean");
        for (int i = 0; i < THREAD_AMOUNT; ++i) {
            Thread e = new Thread(new TaskClean(list, SIZE));
            threads.set(i, e);
            e.start();
        }

        System.out.println("Join TaskClean");
        for (int i = 0; i < THREAD_AMOUNT; ++i) {
            try {
                threads.get(i).join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        if (!list.isEmpty()) {
            System.out.println("Set should be empty");
        }

        System.out.println("Starting TaskAddRandom");
        for (int i = 0; i < THREAD_AMOUNT; ++i) {
            Thread e = new Thread(new TaskAddRandom(list, SIZE));
            threads.set(i, e);
            e.start();
        }

        System.out.println("Starting TaskRemoveRandom");
        for (int i = THREAD_AMOUNT; i < THREAD_AMOUNT * 2; ++i) {
            Thread e = new Thread(new TaskRemoveRandom(list, SIZE));
            threads.add(e);
            e.start();
        }

        System.out.println("Join TaskRemoveRandom and TaskAddRandom");
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        System.out.println("Cleaning of the set");
        for (int i = -SIZE; i < SIZE; ++i) {
            list.remove("" + i);
        }

        if (!list.isEmpty())
            System.out.println("Set should be empty");
    }
}
