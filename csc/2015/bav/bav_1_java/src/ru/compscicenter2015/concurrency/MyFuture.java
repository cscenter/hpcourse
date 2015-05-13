package ru.compscicenter2015.concurrency;

import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

public class MyFuture<V> implements Future<V> {
	private final Callable<V> task;
	private volatile Thread currentThread;
	private volatile V result;
	private volatile Throwable exception;
	private final AtomicInteger state;
	
	private static final int NEW       = 0;
	private static final int RUNNING   = 1;
	private static final int CANCELED  = 2;
	private static final int DONE      = 3;
	private static final int ERROR     = 4;
	
	public MyFuture(Callable<V> task) {
		if (task == null)
			throw new NullPointerException();
		this.task = task;
		exception = null;
		result = null;
		state = new AtomicInteger(NEW);
	}

	public void start() {
	//	System.out.println("Im ready to do smth usefull");
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
			while (state.get() == NEW || state.get() == RUNNING) {
				task.wait();
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
		case ERROR:
			return "Error";
		}
		return "";
	}
}
