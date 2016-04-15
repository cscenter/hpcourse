package communication;

import communication.Protocol.ListTasksResponse.TaskDescription;
import communication.Protocol.Task;
import org.jetbrains.annotations.*;
import org.slf4j.*;

public class Storage {
  private static Logger logger = LoggerFactory.getLogger(Storage.class);

  private static int TIMEOUT = 500;
  private TasksContainer myTasksContainer = new TasksContainer();

  public int registerTask(Task task, String clientId) {
    return myTasksContainer.registerTask(task, clientId);
  }

  public void notifySolved(int taskId, long result) {
    updateTask(taskId, result, FullTask.DONE);
  }

  public void notifyError(int taskId) {
    updateTask(taskId, null, FullTask.ERROR);
  }

  private void updateTask(int taskId, Long result, int state) {
    FullTask task = myTasksContainer.getTask(taskId);
    // firstly result, secondly state
    task.myResult.set(result);
    task.myState.set(state);
    synchronized (task.myTask) {
      task.myTask.notifyAll();
    }
  }

  @Nullable
  public Long getValue(int taskId) {
    FullTask task = myTasksContainer.getTask(taskId);
    while (task.myState.get() == FullTask.UNDONE) {
      logger.debug("waiting for taskId: {}", taskId);
      try {
        synchronized (task.myTask) {
          task.myTask.wait(TIMEOUT);
        }
      } catch (InterruptedException ignore) {}
    }
    logger.debug("value received, taskId: {}", taskId);
    return task.myState.get() == FullTask.ERROR ? null : task.myResult.get();
  }

  public void eachTask(final TaskDescriptionReceiver receiver) {
    myTasksContainer.eachTask(new TaskReceiver() {
      public void processFullTask(@NotNull FullTask fullTask) {
        TaskDescription.Builder builder = TaskDescription.newBuilder()
          .setTaskId(fullTask.myId)
          .setClientId(fullTask.myCliendId)
          .setTask(fullTask.myTask);
        if (fullTask.myState.get() == FullTask.DONE) {
          builder.setResult(fullTask.myResult.get());
        }
        receiver.processTaskDescription(builder.build());
      }
    });
  }
}
