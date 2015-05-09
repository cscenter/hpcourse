package ru.compscicenter2015.concurrency;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

public class MyFixedThreadPool {
	private AtomicBoolean isWorking; 
	
	final private int threadsCount;
	private final Queue<Future<?>> workQueue; 
	private final Worker worker[];
	private final Object lockForTasks = new Object();

	MyFixedThreadPool(int n) {
		if (n <= 0) 
			throw new IllegalArgumentException();
		threadsCount = n;
		
		isWorking = new AtomicBoolean(true);
		worker = new Worker[n];
		workQueue = new LinkedList<Future<?>>();
		for (int i = 0; i < n; i++) 
			worker[i] = new Worker();
		for (int i = 0; i < n; i++)
			worker[i].start();
	}

	public void shutdown() {
		for (int i = 0; i < threadsCount; i++) {
			worker[i].interrupt();
		}
		isWorking.compareAndSet(true, false);
	}
	
	public boolean isShutdown() {
		return isWorking.get();
	}

	public Future<?> submit(Callable<?> task) {
		Future<?> future = new MyFuture<>(task);
		addTaskIntoWorkQueue(future);
		return future;
	}
	
	private void addTaskIntoWorkQueue(Future<?> future) {
		synchronized (lockForTasks) {
			workQueue.add(future);
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
				Thread.currentThread().interrupted(); // Если ктото пытался прервать задачу, которая не прерывается 
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
