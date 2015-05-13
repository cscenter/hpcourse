package ru.compscicenter2015.concurrency;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

class Task implements Runnable {
	static private AtomicLong count = new AtomicLong(0);
	private final int duration;
	private final long id;
	
	Task(int duration) {
		this.duration = duration;
		this.id = getNextUniqId();
	}
	
	static long getNextUniqId() {
		return count.getAndIncrement();
	}
	
	public long getId() {
		return id;
	}

	@Override
	public void run() {
		System.out.println("Start sleeping task with id " + id + " for " + duration / 1000. + " seconds");
		try {
			Thread.sleep(duration);
		} catch (InterruptedException e) {
			System.out.println("We were interupted");
		}
		System.out.println("End task with id " + id);
	}
	
	
}

public class Main {	
	public static void main(String[] args) throws InterruptedException, IOException {
		BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
		System.out.println("Input number of threads: ");
		String input = in.readLine();
		int n = Integer.parseInt(input);
		MyFixedThreadPool pool = new MyFixedThreadPool(n);
		System.out.println("Commands:");
		System.out.println("submit duration[ms]; status id; cancel id; exit");
		while(true) {
			System.out.print("Type command: ");
			input = in.readLine();
			if (input.equals("exit"))
				break;
			else
				if (input.startsWith("status ")) {
					long id = Long.parseLong(input.substring("status ".length()));
					System.out.println(pool.getStatus(id));
				}
				else
					if (input.startsWith("submit ")) {
						int duration = Integer.parseInt(input.substring("status ".length()));
						Task task = new Task(duration);
						System.out.println("Task with id " + task.getId() + " created");
						pool.submit(task, task.getId());
					}
					else
						if (input.startsWith("cancel ")) {
							long id = Long.parseLong(input.substring("status ".length()));
							try {
								if (pool.cancel(id))
									System.out.println("Task with id number " + id + " was successfully canceled");
								else
									System.out.println("Task with id number " + id + " was not canceled");
							} catch(IllegalArgumentException e) {
								System.out.println("Task with id " + id + " is absent");
							}
						}
						else
							System.out.println("Unknown command");
		}
		pool.shutdown();
	}

}
