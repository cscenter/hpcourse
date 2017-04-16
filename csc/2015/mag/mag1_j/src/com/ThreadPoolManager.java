package com.cscenter;

import java.util.concurrent.Callable;

/**
 * Created by Mark on 09.05.2015.
 */
public class ThreadPoolManager {
    private final int CAPACITY;

    private BlockingQueue<Runnable> queue = new BlockingQueue<Runnable>();

    private final Thread[] workers;

    private volatile boolean shutdown;

    private void initAllThreads(){
        for(int i = 0; i < CAPACITY; i ++){
            workers[i] = new Worker(queue, Integer.toString(i));
            workers[i].start();
        }
    }

    public FutureTask<?> submit(Runnable r, Object t){
        FutureTask<?> tmp = new FutureTask<Object>(r, t);
        queue.push(tmp);
        return tmp;
    }

    public FutureTask<?> sumbit(Callable r){
        FutureTask<?> tmp = new FutureTask<Object>(r);
        queue.push(tmp);
        return tmp;
    }


    public ThreadPoolManager(int capacity){
        this.CAPACITY = capacity;
        this.workers = new Thread[capacity];
        initAllThreads();
    }

    public void shutdown(){
        while(!queue.isEmpty()){
            try{
                Thread.sleep(1000);
            }catch (InterruptedException e){
                System.out.println("Interrupting");
            }
        }
        shutdown = true;
        for(Thread worker: workers){
            worker.interrupt();
        }
    }

    public class Worker extends Thread{
        private BlockingQueue<Runnable> queue;
        private String name;



        public Worker(BlockingQueue<Runnable> newQueue, String name){
            this.queue = newQueue;
            this.name = name;
        }

        @Override
        public void run(){
            while(!shutdown){

                Runnable r = queue.pop();
                if(r == null){
                    continue;
                }
                System.out.println("Task taken by thread "+ this.name);
                try {
                    r.run();
                    System.out.println("Task completed by thread " + this.name);
                } catch (Exception re){
                    System.out.println("Caught exception at thread " + this.name);
                    System.out.println(re.getMessage());

                }


            }
        }
    }

}
