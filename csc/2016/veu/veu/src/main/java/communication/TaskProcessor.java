package communication;

import communication.Protocol.*;
import communication.Protocol.Task.Param;
import org.jetbrains.annotations.*;

public class TaskProcessor implements RequestProcessor {
  private final Storage myStorage;

  public TaskProcessor(Storage storage) {
    myStorage = storage;
  }

  @Nullable
  public Protocol.ServerResponse processRequest(Protocol.ServerRequest request) {
    if (!request.hasSubmit()) {
      return null;
    }
    SubmitTask submit = request.getSubmit();
    if (!submit.hasTask()) {
      return null;
    }
    int newId = registerTask(myStorage, submit.getTask(), request.getClientId());

    SubmitTaskResponse submitTaskResponse = SubmitTaskResponse.newBuilder().setSubmittedTaskId(newId).setStatus(Status.OK).build();
    return Protocol.ServerResponse.newBuilder()
      .setSubmitResponse(submitTaskResponse)
      .setRequestId(request.getRequestId()).build();
  }

  int registerTask(Storage storage, @NotNull Task task, String clientId) {
    int id = storage.registerTask(task, clientId);
    new TaskThread(task, id).start();
    return id;
  }

  class TaskThread extends Thread {
    private final int myId;
    private final Protocol.Task myTask;
    private long myN;
    private Param[] myParams;

    private Long[] myVars = new Long[4];

    public TaskThread(@NotNull Protocol.Task task, int id) {
      myTask = task;
      myId = id;
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
        if (myVars[i] == null) {
          myStorage.notifyError(myId);
        }
        recur(i + 1);
      }
    }

    private void solve(long a, long b, long p, long m) {
      while (myN-- > 0) {
        b = (a * p + b) % m;
        a = b;
      }
      myStorage.notifySolved(myId, a);
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
}
