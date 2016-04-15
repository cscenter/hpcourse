package communication;

import org.slf4j.*;

import java.util.concurrent.atomic.AtomicInteger;

public class TasksContainer {
  private static Logger logger = LoggerFactory.getLogger(TasksContainer.class);

  private static final int p1, p2, LEN1, LEN2, LEN1m1;// = 15, p2 = 31 - 15;

  static {
    if (Boolean.parseBoolean(System.getProperty("testEnv", ""))) {
      p1 = 3;
      p2 = 3;
    } else {
      p1 = 15;
      p2 = Integer.SIZE - 1 - p1;
    }
    LEN1 = 1 << p1;
    LEN1m1 = LEN1 - 1;
    LEN2 = 1 << p2;
  }

  private final FullTask[][] myTasks = new FullTask[LEN1][];
  private final AtomicInteger myCounter = new AtomicInteger(0);
  private AtomicInteger myAllocated = new AtomicInteger(0);

  public int registerTask(Protocol.Task task, String clientId) {
    int id = myCounter.getAndIncrement();
    logger.debug("new task: {}", id);
    FullTask fullTask = new FullTask(id, task, clientId);
    int i1 = idx1(id);
    int i2 = idx2(id);
    if (myTasks[i1] == null) {
      boolean alloc = false;
      synchronized (myTasks) {
        if (myTasks[i1] == null) {
          alloc = true;
          myTasks[i1] = new FullTask[LEN2];
        }
      }
      if (alloc) { logger.info("allocated new memory, {}", i1); }
    }
    myTasks[i1][i2] = fullTask;
    myAllocated.incrementAndGet();
    return id;
  }

  public FullTask getTask(int taskId) {
    // we can be sure that myTasks has already initialized in the [i1][i2] position
    return myTasks[idx1(taskId)][idx2(taskId)];
  }

  public void eachTask(TaskReceiver receiver) {
    for (int i = 0, sz = myAllocated.get(); i < sz; i++) {
      FullTask task = getTask(i);
      if (task != null) {
        receiver.processFullTask(task);
      }
    }
  }

  private static int idx1(int id) {
    return id >> p1;
  }

  private static int idx2(int id) {
    return id & LEN1m1;
  }
}
