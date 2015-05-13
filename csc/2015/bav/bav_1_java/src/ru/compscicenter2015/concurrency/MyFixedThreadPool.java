package ru.compscicenter2015.concurrency;

import java.util.LinkedList;
import java.util.Queue;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class MyFixedThreadPool {
	private AtomicLong innerTaskId;
	private AtomicBoolean isWorking; 
	
	final private int threadsCount;
	private final Queue<Future<?>> workQueue; 
	private final Worker worker[];
	private final TreeMap<Long, Future<?>> futureWithId;
	
	private final Object lockForTasks = new Object();

	MyFixedThreadPool(int n) {
		if (n <= 0) 
			throw new IllegalArgumentException();
		threadsCount = n;
		
		innerTaskId = new AtomicLong(0);
		isWorking = new AtomicBoolean(true);
		worker = new Worker[n];
		workQueue = new LinkedList<Future<?>>();
		futureWithId = new TreeMap<Long, Future<?>>();
		for (int i = 0; i < n; i++) 
			worker[i] = new Worker();
		for (int i = 0; i < n; i++)
			worker[i].start();
	}
	
	public boolean cancel(long id) {
		if (!futureWithId.containsKey(id))
			throw new IllegalArgumentException();
		Future <?> future = futureWithId.get(id);
		return future.cancel(true);
	}

	public void shutdown() {
		if (isWorking.compareAndSet(true, false)) {
			for (int i = 0; i < threadsCount; i++) {
				worker[i].interrupt();
			}
		}
	}
	
	public boolean isShutdown() {
		return isWorking.get();
	}
	
	public String getStatus(long id) {
		if (!futureWithId.containsKey(id))
			return "Task with this id is absent";
		Future <?> future = futureWithId.get(id);
		return ((MyFuture<?>) future).getState();
	}
	
	public Future<?> submit(Runnable task) {
		return submit(Executors.callable(task), innerTaskId.getAndIncrement());
	}
	
	public Future<?> submit(Runnable task, long id) {
		return submit(Executors.callable(task), id);
	}

	public Future<?> submit(Callable<?> task) {
		return submit(task, innerTaskId.getAndIncrement());
	}
	
	public Future<?> submit(Callable<?> task, long id) {
		Future<?> future = new MyFuture<>(task);
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

	final class Worker implements Runnable {
		final private Thread thread;

		Worker() {
			thread = new Thread(this);
		}
		
		public void start() {
			thread.start();
		}

		@Override
		public void run() {
			while (isWorking.get()) {
				synchronized (lockForTasks) {
					if (workQueue.isEmpty()) {
						try {
							lockForTasks.wait();
						} catch (InterruptedException e) {
							break;
						}
					}
				} 
				Future<?> future = getTaskFromWorkQueue(); 
				if (future != null) {
					((MyFuture<?>) future).start(); 
				}
			}
		}

		public void interrupt() {
			thread.interrupt();
		}
	}
}
