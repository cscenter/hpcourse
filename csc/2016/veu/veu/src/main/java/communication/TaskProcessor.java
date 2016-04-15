package communication;

import communication.Protocol.*;
import communication.Protocol.Task.Param;
import org.jetbrains.annotations.*;
import org.slf4j.*;

import java.util.Arrays;

public class TaskProcessor implements RequestProcessor {
  private final static Logger logger = LoggerFactory.getLogger(TaskProcessor.class);

  private final Storage myStorage;

  public TaskProcessor(Storage storage) {
    myStorage = storage;
  }

  @Nullable
  public ServerResponse.Builder processRequest(Protocol.ServerRequest request) {
    if (!request.hasSubmit()) {
      return null;
    }
    SubmitTask submit = request.getSubmit();
    if (!submit.hasTask()) {
      return null;
    }
    int newId = registerTask(myStorage, submit.getTask(), request.getClientId());

    SubmitTaskResponse submitTaskResponse = SubmitTaskResponse.newBuilder().setSubmittedTaskId(newId).setStatus(Status.OK).build();
    return Protocol.ServerResponse.newBuilder().setSubmitResponse(submitTaskResponse);
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
      collectParamsAndSolve();
    }

    private void collectParamsAndSolve() {
      logger.debug("Start collecting params, taskId: {}", myId);
      for (int i = 0; i < myParams.length; i++) {
        myVars[i] = calc(myParams[i]);
        if (myVars[i] == null) {
          myStorage.notifyError(myId);
          logger.debug("Error with param: {}", myParams[i]);
          return;
        }
      }
      logger.debug("Finished collecting params, taskId: {}, params: {}", myId, Arrays.toString(myVars));
      Long answer = Util.solve(myVars[0], myVars[1], myVars[2], myVars[3], myN);
      logger.debug("Finished solving, taskId: {}, anser: {}", myId, answer == null ? "NaN" : Long.toString(answer));
      if (answer == null) {
        myStorage.notifyError(myId);
      } else {
        myStorage.notifySolved(myId, answer);
      }
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
