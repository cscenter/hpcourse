package ru.compscicenter2015.concurrency;

import java.util.LinkedList;
import java.util.Queue;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class MyFixedThreadPool {
	private AtomicLong innerTaskId;
	private AtomicBoolean isWorking; 
	final private int threadsCount;
	
	private final Queue<Future<?>> workQueue; 
	private final Worker worker[];
	private final TreeSet<Long> threadSet;
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
		threadSet = new TreeSet<Long>();
		futureWithId = new TreeMap<Long, Future<?>>();
		
		for (int i = 0; i < n; i++) 
			worker[i] = new Worker();
		for (int i = 0; i < n; i++)
			worker[i].start();
	}
	
	public boolean cancel(long id) {
		synchronized (futureWithId) {
			if (!futureWithId.containsKey(id))
				throw new IllegalArgumentException();
			Future <?> future = futureWithId.get(id);
			return future.cancel(true);
		}
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
		synchronized (futureWithId) {
			if (!futureWithId.containsKey(id))
				return "Task with this id is absent";
			Future <?> future = futureWithId.get(id);
			return ((MyFuture<?>) future).getState();	
		}
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
	
	private void addThreadInThreadSet(Thread thread) {
		synchronized (threadSet) {
			threadSet.add(thread.getId());	
		}
	}
	
	public boolean isThreadInThreadSet(Thread thread) {
		synchronized (threadSet) {
			return threadSet.contains(thread.getId());
		}
	}
	
	final class Worker implements Runnable {
		final private Thread thread;

		Worker() {
			thread = new Thread(this);
			addThreadInThreadSet(thread);
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
	public class MyFuture<V> implements Future<V> {
		private final Callable<V> task;
		private volatile Thread currentThread;
		private volatile V result;
		private volatile Throwable exception;
		private final AtomicInteger state;
		
		private static final int NEW        = 0;
		private static final int RUNNING    = 1;
		private static final int CANCELED   = 2;
		private static final int DONE       = 3;
		private static final int ERROR      = 4;
		
		public MyFuture(Callable<V> task) {
			if (task == null)
				throw new NullPointerException();
			this.task = task;
			exception = null;
			result = null;
			//allowWork = true;
			state = new AtomicInteger(NEW);
		}

		public void start() {
			state.compareAndSet(NEW, RUNNING);
			currentThread = Thread.currentThread();
			try {
				result = task.call();
			} catch (Throwable e) {
				state.compareAndSet(RUNNING, ERROR);
				exception = e; 
			} 
			state.compareAndSet(RUNNING, DONE);
			synchronized (task) {
				task.notify();	
			}
		}
		
		@SuppressWarnings("finally")
		@Override
		public boolean cancel(boolean mayInterruptIfRunning) {
			if (state.get() == DONE)
				return false;
			if (state.get() == CANCELED)
				return true;		
			if (state.compareAndSet(NEW, CANCELED)) 
				return true;
			if (mayInterruptIfRunning) {
				try {
					Thread t = null;
					while ((t = currentThread) == null)
						Thread.yield();
					if (t != null) {
						t.interrupt();
					}
				} finally {
					state.set(CANCELED);
				}
			}
			return false;
		}

		@Override
		public boolean isCancelled() {
			return state.get() == CANCELED;
		}

		@Override
		public boolean isDone() {
			return state.get() == DONE;
		}

		@Override
		public V get() throws InterruptedException, ExecutionException {
			synchronized (task) {
				boolean isThreadInPool = isThreadInThreadSet(Thread.currentThread());
				while (state.get() == NEW || state.get() == RUNNING) {
					Future<?> future = null;
					Thread.sleep(10); // т.к. по идее мы уже выполняем какую-то задачу, то пропускаем вперед сначала остальные(незанятые) потоки 
					if (isThreadInPool && (future = getTaskFromWorkQueue()) != null) {
						((MyFuture<?>)future).start();
						//System.out.println(future + " is ready");
					} 
					else {
						task.wait();
					}
				}
			}
			if (state.get() == ERROR)
				throw new ExecutionException(exception);
			if (state.get() == CANCELED)
				throw new CancellationException();
			
			return result;
		}

		@Override
		public V get(long timeout, TimeUnit unit) throws InterruptedException,
				ExecutionException, TimeoutException {
			if (unit == null)
				throw new NullPointerException();
			long deadline = unit.toMillis(timeout) + System.currentTimeMillis();
			synchronized (task) {
				while (System.currentTimeMillis() < deadline && (state.get() == NEW || state.get() == RUNNING)) {
					task.wait(unit.toMillis(timeout));
				}
			}
			if (state.get() == ERROR)
				throw new ExecutionException(exception);
			if (state.get() == CANCELED)
				throw new CancellationException();
			if (state.get() != DONE)
				throw new TimeoutException();
			return result;
		}
		
		public String getState() {
			switch(state.get()) {
			case NEW:
				return "New";
			case RUNNING:
				return "Running";
			case CANCELED:
				return "Canceled";	
			case DONE:
				return "Done";
			}
			return "Error";
		}
	}

}
