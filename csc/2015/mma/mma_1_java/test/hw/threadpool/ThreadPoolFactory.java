package hw.threadpool;

import hw.FixedThreadPool;
import hw.ThreadPool;

public class ThreadPoolFactory {
    public static ThreadPool create(int nThreads){
        return new FixedThreadPool(nThreads);
        //return new ForkJoinPool(nThreads);
    }
}
