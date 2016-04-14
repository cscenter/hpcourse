package communication;

import communication.Protocol.SubmitTask;
import communication.Protocol.Task.Param;
import org.jetbrains.annotations.NotNull;

public class TaskThread extends Thread {
  private final int myId;
  private final Protocol.Task myTask;
  private final Storage myStorage;

  private long myN;
  private Param[] myParams;
  private long[] myVars = new long[4];

  public TaskThread(@NotNull SubmitTask submitTask, Storage storage, int id) {
    assert submitTask.hasTask();
    myTask = submitTask.getTask();
    myStorage = storage;
    myId = id;
  }

  static int registerTask(@NotNull SubmitTask submitTask, Storage storage) {
    assert submitTask.hasTask();
    int id = storage.getNextId();
    new TaskThread(submitTask, storage, id).start();
    return id;
  }

  @Override
  public void run() {
    myParams = new Param[]{myTask.getA(), myTask.getB(), myTask.getP(), myTask.getM()};
    myN = myTask.getN();
    recur(0);
  }

  private void recur(int i) {
    if (i == myParams.length) {
      solve(myVars[0], myVars[1], myVars[2], myVars[3]);
    } else {
      myVars[i] = calc(myParams[i]);
      recur(i + 1);
    }
  }

  private void solve(long a, long b, long p, long m) {
    while (myN-- > 0) {
      b = (a * p + b) % m;
      a = b;
    }
    long result = a;
    myStorage.notifySolved(myId, result);
  }

  private Long calc(Param param) {
    if (param.hasValue()) {
      return param.getValue();
    } else if (param.hasDependentTaskId()) {
      return myStorage.getValue(param.getDependentTaskId());
    } else {
      throw new IllegalStateException("value or dependent task should exist");
    }
  }
}
