package ru.nightuser.hpcource.hw1;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public final class MyPool {
  private final Thread[] workerThreads;
  private final ThreadGroup workerGroup;

  private final LinkedList<MyTask<?>> tasks;
  private final AtomicBoolean running;

  private final Object tasksPollLock;

  public MyPool(int n) {
    workerThreads = new Thread[n];
    workerGroup = new ThreadGroup("workers");

    tasks = new LinkedList<>();
    running = new AtomicBoolean(true);

    tasksPollLock = new Object();

    MyWorker worker = new MyWorker(this);

    for (int i = 0; i < n; ++i) {
      workerThreads[i] = new MyWorkerThread(workerGroup, worker, String.format("worker%d", i));
      workerThreads[i].start();
    }
  }

  public <V> MyTask<V> submit(Callable<V> callable) {
    if (!isRunning()) {
      throw new RejectedExecutionException();
    }

    MyTask<V> task;
    synchronized (tasks) {
      synchronized (tasksPollLock) {
        task = new MyTask<>(workerGroup, callable);

        tasks.add(task);
        tasks.notify();
      }
    }

    return task;
  }

  public void shutdown() {
    boolean interrupt = false;

    try {
      synchronized (tasksPollLock) {
        while (!tasks.isEmpty()) {
          tasksPollLock.wait();
        }

        running.set(false);

        synchronized (tasks) {
          tasks.notifyAll();
        }
      }
    } catch (InterruptedException ie) {
      interrupt = true;
    }

    if (interrupt) {
      Thread.currentThread().interrupt();
    }
  }

  public List<Callable<?>> shutdownNow() {
    synchronized (tasks) {
      for (Thread workerThread : workerThreads) {
        workerThread.interrupt();
      }
    }

    return tasks.stream()
        .filter(MyTask::isRunning)
        .map(MyTask::getCallable)
        .collect(Collectors.toList());
  }

  boolean isRunning() {
    return running.get();
  }

  Queue<MyTask<?>> getTasks() {
    return tasks;
  }

  Object getTasksPollLock() {
    return tasksPollLock;
  }
}
