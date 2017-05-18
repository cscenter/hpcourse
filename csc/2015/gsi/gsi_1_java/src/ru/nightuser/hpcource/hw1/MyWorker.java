package ru.nightuser.hpcource.hw1;

import java.util.Queue;
import java.util.concurrent.atomic.AtomicReference;

final class MyWorker implements Runnable {
  private final MyPool pool;
  private final Queue<MyTask<?>> tasks;
  private final Object tasksPollLock;
  private final AtomicReference<Thread> workerThreadRef;

  MyWorker(MyPool pool) {
    this.pool = pool;
    tasks = pool.getTasks();
    tasksPollLock = pool.getTasksPollLock();

    workerThreadRef = new AtomicReference<>();
  }

  @Override
  public void run() {
    workerThreadRef.set(Thread.currentThread());
    try {
      while (fetchAndRunTask(null) != null) ;
    } catch (InterruptedException ie) {}
  }

  MyTask<?> fetchAndRunTask(MyTask<?> awaitingTask) throws InterruptedException {
    MyTask<?> task;
    synchronized (tasks) {
      while ((awaitingTask == null || !awaitingTask.isDone()) &&
          pool.isRunning() &&
          tasks.isEmpty()) {
        tasks.wait();
      }

      if (awaitingTask != null && awaitingTask.isDone()) {
        tasks.notify();
        return null;
      }

      if (!pool.isRunning() || Thread.currentThread().isInterrupted()) {
        return null;
      }

      task = tasks.poll();

      synchronized (tasksPollLock) {
        tasksPollLock.notifyAll();
      }
    }

    task.setWorker(this);
    task.call();
    if (task.isAwaited()) {
      synchronized (tasks) {
        tasks.notifyAll();
      }
    }

    return task;
  }

  boolean cancel() {
    workerThreadRef.get().interrupt();
    return workerThreadRef.get().isInterrupted();
  }

  Thread getWorkerThread() {
    return workerThreadRef.get();
  }
}
