package hw.threadpool;

import hw.ThreadPool;

import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

public class ForkJoinPool implements ThreadPool {
    java.util.concurrent.ForkJoinPool impl = new java.util.concurrent.ForkJoinPool();

    public ForkJoinPool(int nThreads){
        impl = new java.util.concurrent.ForkJoinPool(nThreads);
    }

    public void submit(Runnable f){
        impl.submit(f);
    }

    @Override
    public void awaitAll() throws InterruptedException {
        this.impl.awaitQuiescence(10, TimeUnit.SECONDS);
    }
}
