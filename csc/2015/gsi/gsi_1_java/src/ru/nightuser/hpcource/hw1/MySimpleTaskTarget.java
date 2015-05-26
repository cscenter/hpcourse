package ru.nightuser.hpcource.hw1;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

public class MySimpleTaskTarget implements Callable<Integer> {
  private int id;
  private long millis;

  public MySimpleTaskTarget(int id, long millis) {
    this.id = id;
    this.millis = millis;
  }

  @Override
  public Integer call() throws Exception {
    System.out.printf("@started %d%n", id);
    try {
      Thread.sleep(millis);
    } catch (InterruptedException ie) {
      System.out.printf("@interrupted %d%n", id);
      Thread.currentThread().interrupt();
      throw new ExecutionException("Interrupted!", ie);
    }
    System.out.printf("@finished %d%n", id);
    return 42 + id;
  }
}
