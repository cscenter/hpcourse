package ru.compscicenter2015.concurrency;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

class TaskSum implements Callable<Long> {
	private final long n;
	TaskSum(long n) {
		this.n = n;
	}
		
	@Override
	public Long call() throws Exception {
		long sum = 0;
		for (int i = 1; i <= n; i++)
			sum += i;
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
		pool = new MyFixedThreadPool(10);
	}
	
	@Test
	public void divideByZeroInTask() {
		Future <?> future = pool.submit(new TaskSum(0));
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
	public void testTenThreadAndManyTask() {
		ArrayList <Future<?>> futureList = new ArrayList<Future<?>>();
		for (int i = 1; i <= 100; i++)
			futureList.add(pool.submit(new TaskSum(i)));
		for (int i = 1; i <= 100; i++) {
			long value = 0;
			try {
				value = (long) futureList.get(i - 1).get();
			} catch (InterruptedException | ExecutionException e) {
				value = -1;
			}
			assertEquals("Wrong answer", value, (i * (i + 1)) / 2);
		}
	}
	
	@Test
	public void testsWithTimedGet() {
		Future <?> f = pool.submit(new TaskSum(100_000_000L));
		try {
			long res = (long)f.get(2L, TimeUnit.SECONDS);
			long realRes = (100_000_000L * 100_000_001) / 2L;
			assertEquals("We had, but result is not right", realRes, res);
		} catch (InterruptedException e) {
			assertTrue(false);
		} catch (ExecutionException e) {
			assertTrue(false);
		} catch (TimeoutException e) {
			assertTrue(false);
		}
		
		f = pool.submit(new TaskSum(1_000_000_000_000_000_000L));
		try {
			f.get(2L, TimeUnit.SECONDS);
			assertTrue("You have very fast computer ;)", false);
		} catch (InterruptedException e) {
			assertTrue(false);
		} catch (ExecutionException e) {
			assertTrue(false);
		} catch (TimeoutException e) {
			assertTrue(true);
		}

		
	}
	
	//@Test(expected = ArithmeticException.class)

	
	//Еще 2 теста на прерывание, создать еще одну задачу которую можно и которую нельзя прервать
	
	//схожие тесты когда много потоков, миллион
	
	// Для проверки как выполняются подзадачи реализовать merge sort
	
	@After
	public void shutdown() {
		pool.shutdown();
	}
}
