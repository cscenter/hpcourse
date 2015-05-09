package ru.compscicenter2015.concurrency;

public class Main {	
	public static void main(String[] args) throws InterruptedException {
		System.out.println("This is the main thread");
		MyFixedThreadPool pool = new MyFixedThreadPool(4);
		
		
		pool.shutdown();
	}

}
