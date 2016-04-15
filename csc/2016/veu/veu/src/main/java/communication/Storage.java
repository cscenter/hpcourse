package communication;

import communication.Protocol.ListTasksResponse.TaskDescription;
import communication.Protocol.Task;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;

public class Storage {
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
      try {
        synchronized (task.myTask) {
          task.myTask.wait(TIMEOUT);
        }
      } catch (InterruptedException ignore) {}
    }
    return task.myState.get() == FullTask.ERROR ? null : task.myResult.get();
  }

  public Iterable<? extends TaskDescription> getTasks() {
    return new Iterable<TaskDescription>() {
      public Iterator<TaskDescription> iterator() {
        return new Iterator<TaskDescription>() {
          final Iterator<FullTask> myTasks = myTasksContainer.iterTasks();
          public boolean hasNext() {
            return myTasks.hasNext();
          }

          public TaskDescription next() {
            FullTask cur = myTasks.next();
            TaskDescription.Builder builder = TaskDescription.newBuilder()
              .setTaskId(cur.myId)
              .setClientId(cur.myCliendId)
              .setTask(cur.myTask);
            if (cur.myState.get() == FullTask.DONE) {
              builder.setResult(cur.myResult.get());
            }
            return builder.build();
          }
        };
      }
    };
  }
}
