package iak;

import sun.awt.Mutex;

import java.util.Random;

/**
 * Created by iak on 09.04.17.
 */

public class SetTest {

    public static void main(String[] strings)
    {
        test_concurrent(10);
    }

    private static void test_concurrent(int nthreads)
    {
        LockFreeSet set = new LockFreeSetImpl<Integer>();
        Thread[] threads = new SetThread[nthreads];
        for (int i = 0; i < nthreads; i++) {
            threads[i] = new SetThread(set);
        }
        for (int i = 0; i < nthreads; i++) {
            threads[i].start();
        }
        for (int i = 0; i < nthreads; i++) {
            try {
                threads[i].join();
            }
            catch (Exception e) {
                System.out.format("Thread %d ended execution\n", i);
            }
        }
    }

    private static class SetThread extends Thread {

        LockFreeSet set;

        SetThread(LockFreeSet set)
        {
            this.set = set;
        }

        int add = 0;
        int add_succ = 0;
        int rem = 0;
        int rem_succ = 0;
        int cont = 0;
        int cont_succ = 0;
        int empty = 0;
        int empty_true = 0;

        public void run()
        {
            int nact = 10000;
            int cap = 30;

            Random gen = new Random();
            while (nact-- != 0) {
                switch (gen.nextInt() % 4) {
                    case 0:
                        add++;
                        add_succ += (set.add(gen.nextInt(cap)) ? 1 : 0);
                        break;
                    case 1:
                        cont++;
                        cont_succ += (set.contains(gen.nextInt(cap)) ? 1 : 0);
                        break;
                    case 2:
                        rem++;
                        rem_succ += (set.remove(gen.nextInt(cap)) ? 1 : 0);
                        break;
                    default:
                        empty++;
                        empty_true += (set.isEmpty() ? 1 : 0);
                }
            }

            print_stat();
        }

        private void print_stat()
        {
            System.out.format("Thread %d finishes\n", getId());
            System.out.format("Add: %d(%d)\n", add_succ, add);
            System.out.format("Remove: %d(%d)\n", rem_succ, rem);
            System.out.format("Contatins: %d(%d)\n", cont_succ, cont);
            System.out.format("Empty: %d(%d)\n\n", empty_true, empty);
        }
    }
}
