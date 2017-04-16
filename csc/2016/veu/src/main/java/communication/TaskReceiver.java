package communication;

import org.jetbrains.annotations.NotNull;

public interface TaskReceiver {
  void processFullTask(@NotNull FullTask task);
}
