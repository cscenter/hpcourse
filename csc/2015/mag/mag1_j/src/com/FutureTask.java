package com.cscenter;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by Mark on 09.05.2015.
 */
public class FutureTask<V> implements Future<V>, Runnable{
    private AtomicInteger status;
    private  final Callable<V> callable;
    private V result;
    private volatile Thread runner;
    private Throwable exception;
    private final int DONE = 0;
    private final int RUNNING = 1;
    private final int CANCELLED = 2;
    private final int INIT = -1;
    /*
        status:
            0 - Done
            1 - running
            2 - canceled
            -1 - init
     */
    public FutureTask(Callable<V> callable){
        if(callable == null){
            throw new NullPointerException();
        }
        this.callable = callable;
        this.result = null;
        this.status = new AtomicInteger(INIT);
    }

    public FutureTask(Runnable runnable, V result){
        if(runnable == null){
            throw new NullPointerException();
        }
        this.callable = Executors.callable(runnable, result);
        this.result = null;
        this.status = new AtomicInteger(-1);
    }

    public boolean isCancelled(){
        return (status.get() == CANCELLED);
    }

    public boolean isDone(){
        return (status.get() == DONE) && (runner == null);
    }

    public boolean cancel(boolean mayInterruptIfRunning){
        int state = status.get();
        if((state == DONE) || (state == CANCELLED) || !status.compareAndSet(state, CANCELLED)){
            return false;
        }
        if(mayInterruptIfRunning){
            if(this.runner != null){
                this.runner.interrupt();
            }
            runner = null;
        }
        return false;
    }

    public V get() throws InterruptedException, ExecutionException{
        synchronized (callable){
            if(status.get() == CANCELLED){
                throw new CancellationException();
            }
            if (exception != null){
                throw new ExecutionException("Wooops", exception);
            }
            return result;
        }
    }

    public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException{
        synchronized (callable) {
            long tmp = unit.toMillis(timeout) + System.currentTimeMillis();
            while ((status.get() != DONE) && (status.get() != CANCELLED) && (System.currentTimeMillis() < tmp)){
                callable.wait(unit.toMillis(timeout));
            }
            int state = status.get();
            if(state == CANCELLED){
                throw new CancellationException();
            }
            if(exception != null){
                throw new ExecutionException("Wooops", exception);
            }
            if(state != DONE){
                throw new TimeoutException();
            }
            return result;
        }
    }

    public void run(){
        if(!status.compareAndSet(INIT, RUNNING)){
            return;
        }
        try{
            runner = Thread.currentThread();
            result = callable.call();

        }catch (Exception e) {
            exception = e;
            result = null;
        }finally {
            runner = null;
            int state = status.get();
            if(state != DONE && state != CANCELLED){
                status.set(DONE);
            }
        }

    }
}
