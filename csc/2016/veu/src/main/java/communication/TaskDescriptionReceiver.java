package communication;

import communication.Protocol.ListTasksResponse.TaskDescription;
import org.jetbrains.annotations.NotNull;

public interface TaskDescriptionReceiver {
  void processTaskDescription(@NotNull TaskDescription taskDescription);
}
