package ru.compscicenter2015.concurrency;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.FutureTask;

public class Main {	
	public static void main(String[] args) throws InterruptedException {
		System.out.println("This is the main thread");
		MyFixedThreadPool pool = new MyFixedThreadPool(2);
		
		/*
		Future <?> f = pool.submit(new TaskSumCannotInterrupt(1_000_000_000_000_000_000L));
		Future <?> f2 = pool.submit(new TaskSumCannotInterrupt(10L));
		try {
			try {
				System.out.println(f.get(5L, TimeUnit.SECONDS));
			} catch (TimeoutException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} catch (ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		try {
			System.out.println(f2.get());
		} catch (ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		*/
	
		Future <?> f = pool.submit(new TaskSumCannotInterrupt((long)1e9));
		Future <?> f2 = pool.submit(new TaskSumCannotInterrupt((long)1e7));
		Future <?> f3 = pool.submit(new TaskSumCannotInterrupt(1000L));
		try {
			Thread.currentThread().sleep(500);
			f.cancel(true);
			System.out.println(f.get());
		} catch (ExecutionException | CancellationException e) {
			// TODO Auto-generated catch block
		//	e.printStackTrace();
			System.out.println("first was canceled");
		}
		try {
			Thread.currentThread().sleep(500);
			f2.cancel(true);
			System.out.println(f2.get());
		} catch (ExecutionException | CancellationException e) {
			// TODO Auto-generated catch block
		//	e.printStackTrace();
			System.out.println("second was canceled");
		}
		try {
			System.out.println(f3.get());
		} catch (ExecutionException e) {
			// TODO Auto-generated catch block
			//e.printStackTrace();
		}
		
		//pool.shutdown();
	}

}
