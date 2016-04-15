package communication;

import java.util.concurrent.atomic.*;

public class FullTask {
  final static int DONE = 1, UNDONE = 0, ERROR = -1;

  final int myId;
  final String myCliendId;
  final Protocol.Task myTask;

  AtomicInteger myState;
  AtomicLong myResult;

  public FullTask(int id, Protocol.Task task, String clientId) {
    myId = id;
    myTask = task;
    myState = new AtomicInteger(UNDONE);
    myResult = new AtomicLong(0);
    myCliendId = clientId;
  }
}
