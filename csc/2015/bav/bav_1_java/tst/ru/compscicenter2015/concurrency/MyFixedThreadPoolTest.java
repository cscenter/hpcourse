package ru.compscicenter2015.concurrency;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

class TaskSumCannotInterrupt implements Callable<Long> {
	private final long n;
	TaskSumCannotInterrupt(long n) {
		this.n = n;
	}
		
	@Override
	public Long call() throws Exception {
		long sum = 0;
		for (int i = 1; i <= n; i++)
			sum += i;
		if (n == 0)
			sum /= n; // деление на ноль
		return sum;
	}	
}

class TaskSumCanInterrupt implements Callable<Long> {
	private final long n;
	TaskSumCanInterrupt(long n) {
		this.n = n;
	}
		
	@Override
	public Long call() throws Exception {
		long sum = 0;
		for (int i = 1; i <= n; i++) {
			if (Thread.currentThread().interrupted())
				return Long.MIN_VALUE; // или что-то еще другое сделать, в любом случае это значение мы не получим т.к. get вернет CancelationException
			sum += i;
		}
		if (n == 0)
			sum /= n; // деление на ноль
		//Thread.currentThread().sleep(5000);
		return sum;
	}	
}


public class MyFixedThreadPoolTest {
	private MyFixedThreadPool pool;
	@Before
	public void init() {
		pool = new MyFixedThreadPool(1000);
	}
	
	@Test
	public void divideByZeroInTask() {
		Future <?> future = pool.submit(new TaskSumCannotInterrupt(0));
		try {
			future.get();
			assertTrue("Expected exception", false);
		} catch (InterruptedException e) {
			assertTrue("Not expected InterruptedException", false);
		} catch (ExecutionException e) { 
			assertTrue(true);
		} 
	}
	
	@Test
	public void testManyThreadsAndMoreTasks() {
		ArrayList <Future<?>> futureList = new ArrayList<Future<?>>();
		for (int i = 1; i <= 100000; i++)
			futureList.add(pool.submit(new TaskSumCannotInterrupt(i)));
		for (int i = 1; i <= 100000; i++) {
			long value = 0;
			try {
				value = (long) futureList.get(i - 1).get();
			} catch (InterruptedException | ExecutionException e) {
				value = -1;
			}
			assertEquals("Wrong answer", value, ((long)i * (i + 1)) / 2);
		}
	}
	
	@Test
	public void testsWithTimedGet() {
		Future <?> f = pool.submit(new TaskSumCannotInterrupt(100_000_000L));
		try {
			long res = (long)f.get(2L, TimeUnit.SECONDS);
			long realRes = (100_000_000L * 100_000_001) / 2L;
			assertEquals("We had, but result is not right", realRes, res);
		} catch (InterruptedException e) {
			assertTrue("Not expected InterruptedException", false);
		} catch (ExecutionException e) {
			assertTrue("Not expected ExecutionException", false);
		} catch (TimeoutException e) {
			assertTrue("Not expected TimeoutException", false);
		}
		
		f = pool.submit(new TaskSumCannotInterrupt(1_000_000_000_000_000_000L));
		try {
			f.get(2L, TimeUnit.SECONDS);
			assertTrue("You have very fast computer ;)", false);
		} catch (InterruptedException e) {
			assertTrue("Not expected InterruptedException", false);
		} catch (ExecutionException e) {
			assertTrue("Not expected ExecutionException", false);
		} catch (TimeoutException e) {
			assertTrue(true);
			f.cancel(true);
		}

		
	}

	@Test 
	public void testWhenWeCanInterrupt() throws InterruptedException {
		Future <?> future = pool.submit(new TaskSumCanInterrupt(1_000_000_000_000_000L));
		Thread.currentThread().sleep(100);
		future.cancel(true);
		try {
			future.get();
		} catch (ExecutionException e) {
			assertTrue("Unexpected ExecutionException", false);
		} catch (CancellationException e) {
			assertTrue(true);
		}
	}
	
	@Test 
	public void testWhenWeCanNotInterrupt() throws InterruptedException {
		Future <?> future = pool.submit(new TaskSumCanInterrupt(1_000_000_000_000_000L));
		Thread.currentThread().sleep(100);
		future.cancel(true);
		try {
			future.get();
		} catch (ExecutionException e) {
			assertTrue("Unexpected ExecutionException", false);
		} catch (CancellationException e) {
			assertTrue(true);
		}
	}
	
	@After
	public void shutdown() {
		pool.shutdown();
	}
}
