package communication;

import org.slf4j.*;

import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;

public class TasksContainer {
  private static Logger logger = LoggerFactory.getLogger(TasksContainer.class);

  private static final int p1, p2, LEN1, LEN2;// = 15, p2 = 31 - 15;
//  private static final int LEN1 = 1 << p1, LEN2 = 1 << p2;

  static {
    if (Boolean.parseBoolean(System.getProperty("testEnv", ""))) {
      p1 = 3;
      p2 = 3;
    } else {
      p1 = 15;
      p2 = Integer.SIZE - 1 - p1;
    }
    LEN1 = 1 << p1;
    LEN2 = 1 << p2;
  }

  private final FullTask[][] myTasks = new FullTask[LEN1][];
  private final AtomicInteger myCounter = new AtomicInteger(0);

  public int registerTask(Protocol.Task task, String clientId) {
    int id = myCounter.getAndIncrement();
    logger.debug("new task: " + id + ", " + task.toString().replace('\n', ' '));
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
    return id;
  }

  public FullTask getTask(int taskId) {
    // we can be sure that myTasks has already initialized in the [i1][i2] position
    return myTasks[idx1(taskId)][idx2(taskId)];
  }

  public Iterator<FullTask> iterTasks() {
    return new Iterator<FullTask>() {
      private int pos = 0;
      public boolean hasNext() {
        return pos < myCounter.get();
      }

      public FullTask next() {
        int i1 = idx1(pos);
        int i2 = idx2(pos);
        pos++;
        return myTasks[i1][i2];
      }
    };
  }

  private static int idx1(int id) {
    return id >> p1;
  }

  private static int idx2(int id) {
    return id & (LEN1 - 1);
  }
}
