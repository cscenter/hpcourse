/**
 * Created by alexander on 09.05.15.
 */

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.Map;
public class My_scheduler {
    public enum Status {NEW, INPROGRESS, DONE, CANCELLED, ERROR}
    private volatile boolean isAlive;
    private final Object lock_for_threads;
    private final Queue<Future<?>> queue_of_tasks;
    private final Worker[] pool_of_workers;
    private final Map<Integer, Future<?>> table_of_futures;
    My_scheduler (int count_of_threads) {
        this.isAlive = true;
        this.lock_for_threads = new Object();
        this.queue_of_tasks = new LinkedList<Future<?>>();
        this.pool_of_workers = new Worker[count_of_threads];
        this.table_of_futures = new HashMap<Integer, Future<?>>();
        for (int i = 0; i < count_of_threads; i++ ) {
            this.pool_of_workers[i] = new Worker();
            this.pool_of_workers[i].start();
        }
    }

    public void shut_down() {
        isAlive = false;
        for (int i = 0;i < pool_of_workers.length; i++) {
            pool_of_workers[i].interrupt();
        }
    }

    Future<?> submit(Callable<?> task, int task_id) {
        Future<?> new_future = new Future_of_task(task);
        table_of_futures.put(task_id, new_future);
        synchronized (lock_for_threads) {
            queue_of_tasks.add(new_future);
            lock_for_threads.notifyAll();
        }
        return new_future;
    }
    public boolean cancel_id(int task_id) throws IllegalArgumentException{
        if (table_of_futures.containsKey(task_id)){
            return table_of_futures.get(task_id).cancel(true);
        }
        else {
            throw new IllegalArgumentException();
        }
    }
    public String get_Status(int task_id) {
        return ((Future_of_task<?>)table_of_futures.get(task_id)).get_Status();
    }




    class Worker extends Thread {
        @Override
        public void run() {
            while (isAlive) {
                synchronized (lock_for_threads) {
                    try {
                        if (queue_of_tasks.isEmpty())
                            lock_for_threads.wait();
                    }
                    catch (InterruptedException e) {
                        break;

                    }
                }
                Future<?> new_future = null;
                synchronized (lock_for_threads) {
                    if (!queue_of_tasks.isEmpty()) {
                    new_future = (Future_of_task<?>)queue_of_tasks.poll();
                    }
                }
                Thread.currentThread().interrupted();
                if (new_future != null) {
                    ((Future_of_task<?>)new_future).start();
                }
            }
        }
    }
    class Future_of_task<V> implements Future<V> {
        private Exception exception;
        private volatile V result;
        private volatile Status status_of_task;
        private final Callable<V> task;
        private volatile Thread thread;
        Future_of_task (Callable<V> t) {
            this.task = t;
            this.status_of_task = Status.NEW;
            this.result = null;
        }
        public void start() {
            thread = Thread.currentThread();
            try{
                status_of_task = Status.INPROGRESS;
                result = task.call();
            }
            catch (Exception e) {
                status_of_task = Status.ERROR;
                exception = e;
            }
            if (status_of_task == Status.INPROGRESS){
                status_of_task = Status.DONE;
                synchronized (task) {
                    task.notify();
                }
            }
        }
        public boolean cancel(boolean mayInterruptIfRunning){
            if (status_of_task == Status.ERROR || status_of_task == Status.DONE){
                return false;
            }
            if (status_of_task == Status.NEW) {
                status_of_task = Status.CANCELLED;
                return true;
            }
            if (status_of_task == Status.CANCELLED) {
                return true;
            }
            if (mayInterruptIfRunning) {
                status_of_task = Status.CANCELLED;
                if(thread != null) {
                    thread.interrupt();
                }
                return true;
            }
            return false;
        }
        public boolean isCancelled(){
            return status_of_task == Status.CANCELLED;
        }
        public boolean isDone(){
            return status_of_task == Status.DONE;
        }
        public V get() throws InterruptedException, ExecutionException {
            synchronized(task) {
                while (status_of_task != Status.DONE && status_of_task != Status.ERROR && status_of_task != Status.CANCELLED) {
                    task.wait();
                }
            }
            if (status_of_task == Status.ERROR){
                throw new ExecutionException(exception);
            }
            if (status_of_task == Status.CANCELLED){
                throw new CancellationException();
            }
            return result;
        }
        public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException{
            long deadline = System.currentTimeMillis() + unit.toMillis(timeout);
            synchronized(task) {
                while (status_of_task != Status.DONE || status_of_task != Status.ERROR || status_of_task != Status.CANCELLED) {
                    task.wait(deadline);
                    if (System.currentTimeMillis() > deadline){
                        break;
                    }
                }
            }
            if (status_of_task == Status.ERROR){
                throw new ExecutionException(exception);
            }
            if (status_of_task == Status.CANCELLED){
                throw new CancellationException();
            }
            if (status_of_task != Status.DONE){
                throw new TimeoutException();
            }
            return result;
        }
        public String get_Status() {
            if (status_of_task == Status.DONE)
                return "DONE";
            if (status_of_task == Status.INPROGRESS)
                return "INPROGRESS";
            if (status_of_task == Status.CANCELLED)
                return "CANCELLED";
            if (status_of_task == Status.ERROR)
                return "ERROR";
            return "NEW";
        }
    }
}
