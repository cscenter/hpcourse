package org.mylnikov.concurrency;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by alex on 5/13/2015.
 */
public class MylPool{
    private AtomicBoolean isActive= new AtomicBoolean(true);
    private final HashMap<Long, Future<?>> futureWithId;
    final private int threadsCount;
    private final LinkedList<Future<?>> workQueue;
    private final List<MylWorker> worker;


    private final Object lockForTasks = new Object();

    MylPool(int n) {
        if (n <= 0)
            throw new IllegalArgumentException();
        threadsCount = n;
        worker = Collections.synchronizedList(new ArrayList<MylWorker>());
        workQueue = new LinkedList<>();
        futureWithId = new HashMap<>();
        for (int i = 0; i < n; i++) {
            worker.add(new MylWorker());

        }
        for (int i = 0; i < n; i++)
            worker.get(i).start();
    }

    public boolean cancel(long id) {
        synchronized (lockForTasks) {
            if (!futureWithId.containsKey(id))
                throw new IllegalArgumentException();
            Future<?> future = futureWithId.get(id);
            return future.cancel(true);
        }

    }

    public void shutdown() {
        for (int i = 0; i < threadsCount; i++) {
            worker.get(i).interrupt();
        }
        isActive.compareAndSet(true, false);
    }

    public boolean isShutdown() {
        return !isActive.get();
    }

    public String getStatus(long id) {
        if (!futureWithId.containsKey(id))
            return "No info about task";
        Future <?> future = futureWithId.get(id);
        return ((MylFeature<?>) future).getStringStatusOfFea();
    }

    public Future<?> submit(Callable<?> task, long id) {
        Future<?> future = new MylFeature<>(task);
        addTaskIntoWorkQueue(future, id);
        return future;
    }

    private void addTaskIntoWorkQueue(Future<?> future, long id) {
        synchronized (lockForTasks) {
            workQueue.add(future);
            futureWithId.put(id, future);
            lockForTasks.notifyAll();
        }
    }

    private Future<?> getTaskFromWorkQueue() {
        synchronized (lockForTasks) {
            if (!workQueue.isEmpty()) {
                return workQueue.poll();
            }
            else
                return null;
        }
    }

   final class MylWorker implements Runnable {
       final private Thread thread;

       MylWorker() {
           thread = new Thread(this);
       }

       public void start() {
           thread.start();
       }

       @Override
       public void run() {
           while (isActive.get()) {
               synchronized (lockForTasks) {
                   if (workQueue.isEmpty()) {
                       try {
                           lockForTasks.wait();
                       } catch (InterruptedException e) {
                           break;
                       }
                   }
               }
               Thread.currentThread().interrupted();
               Future<?> future = getTaskFromWorkQueue();
               if (future != null) {
                   ((MylFeature<?>) future).start();
               }
           }
       }

       public void interrupt() {
           this.thread.interrupt();
       }
   }


}