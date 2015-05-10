package ru.nightuser.hpcource.hw1;

public class MyWorkerThread extends Thread {
  private MyWorker worker;

  public MyWorkerThread(ThreadGroup group, MyWorker worker, String name) {
    super(group, worker, name);
    this.worker = worker;
  }

  public MyWorker getWorker() {
    return worker;
  }
}
