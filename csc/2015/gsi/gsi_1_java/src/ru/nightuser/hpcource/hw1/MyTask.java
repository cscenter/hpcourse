package ru.nightuser.hpcource.hw1;

import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class MyTask<V> implements Future<V> {
  private final ThreadGroup workerGroup;
  private final AtomicReference<MyWorker> workerRef;

  private final Callable<V> callable;
  private Exception callableException;

  private final AtomicInteger status;
  private final AtomicBoolean awaited;

  private final AtomicReference<V> resultRef;
  private final Object resultLock;

  public static final int NORMAL = 0;
  public static final int RUNNING = 1;
  public static final int DONE = 5;
  public static final int CANCELLED = 7;

  public MyTask(ThreadGroup workerGroup, Callable<V> callable) {
    this.workerGroup = workerGroup;
    this.callable = callable;

    workerRef = new AtomicReference<>();

    status = new AtomicInteger(NORMAL);
    awaited = new AtomicBoolean(false);

    resultRef = new AtomicReference<>();
    resultLock = new Object();
  }

  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    if (isDone()) {
      return isCancelled();
    }

    if (status.compareAndSet(NORMAL, CANCELLED)) {
      return true;
    }

    if (!mayInterruptIfRunning && status.get() == RUNNING) {
      return false;
    }

    if (mayInterruptIfRunning) {
      // TODO:
      if (!workerRef.get().cancel()) {
        return false;
      }
    }

    return status.compareAndSet(RUNNING, CANCELLED) || isCancelled();
  }

  @Override
  public boolean isCancelled() {
    return (status.get() & CANCELLED) == CANCELLED;
  }

  @Override
  public boolean isDone() {
    return (status.get() & DONE) == DONE;
  }

  public boolean isRunning() {
    return status.get() == RUNNING;
  }

  public boolean isNormal() {
    return status.get() == NORMAL;
  }

  public int getStatus() {
    return status.get();
  }

  boolean isAwaited() {
    return awaited.get();
  }

  @Override
  public V get() throws InterruptedException, ExecutionException {
    if (!isDone() && Thread.currentThread().getThreadGroup().equals(workerGroup)) {
      awaited.set(true);

      MyWorkerThread workerThread = (MyWorkerThread) Thread.currentThread();
      MyWorker worker = workerThread.getWorker();

      while (worker.fetchAndRunTask(this) != null) ;
    }

    synchronized (resultLock) {
      while (!isDone()) {
        resultLock.wait();
      }
    }

    if (isCancelled()) {
      throw new CancellationException();
    }

    if (callableException != null) {
      throw new ExecutionException(callableException);
    }

    return resultRef.get();
  }

  @Override
  public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
    synchronized (resultLock) {
      long timeExpiration = System.currentTimeMillis() + unit.toMillis(timeout);
      while (!isDone()) {
        resultLock.wait(timeExpiration - System.currentTimeMillis());
        if (System.currentTimeMillis() > timeExpiration) {
          throw new TimeoutException();
        }
      }
    }

    if (isCancelled()) {
      throw new CancellationException();
    }

    if (callableException != null) {
      throw new ExecutionException(callableException);
    }

    return resultRef.get();
  }

  void call() throws InterruptedException {
    try {
      if (status.compareAndSet(NORMAL, RUNNING)) {
        resultRef.set(callable.call());

        if (workerRef.get().getWorkerThread().isInterrupted()) {
          throw new InterruptedException();
        }
      }
    } catch (Exception e) {
      callableException = e;

      if (e instanceof InterruptedException) {
        throw (InterruptedException) e;
      }
    } finally {
      status.compareAndSet(RUNNING, DONE);
      synchronized (resultLock) {
        resultLock.notifyAll();
      }
    }
  }

  public Callable<V> getCallable() {
    return callable;
  }

  MyWorker getWorker() {
    return workerRef.get();
  }

  void setWorker(MyWorker worker) {
    workerRef.set(worker);
  }
}
