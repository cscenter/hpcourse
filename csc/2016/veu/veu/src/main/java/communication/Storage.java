package communication;

import org.w3c.dom.Attr;

public class Storage {
  private int myNextId;
  private Iterable<? extends Protocol.ListTasksResponse.TaskDescription> myTasks;

  public int getNextId() {
    return myNextId;
  }

  public void notifySolved(int id, long result) {

  }

  public Long getValue(int taskId) {
    return null;
  }

  public Long getValueImmediately(int taskId) {
    return null;
  }

  public Iterable<? extends Protocol.ListTasksResponse.TaskDescription> getTasks() {
    return myTasks;
  }
}
