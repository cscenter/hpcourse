package communication;

import communication.Protocol.ListTasksResponse;
import org.jetbrains.annotations.*;

public class ListProcessor implements RequestProcessor{
  private final Storage myStorage;

  public ListProcessor(Storage storage) {
    myStorage = storage;
  }

  @Nullable
  public Protocol.ServerResponse.Builder processRequest(Protocol.ServerRequest request) {
    if (!request.hasList()) {
      return null;
    }
    final ListTasksResponse.Builder builder = ListTasksResponse.newBuilder().setStatus(Protocol.Status.OK);
    myStorage.eachTask(new TaskDescriptionReceiver() {
      public void processTaskDescription(@NotNull ListTasksResponse.TaskDescription taskDescription) {
        builder.addTasks(taskDescription);
      }
    });
    return Protocol.ServerResponse.newBuilder().setListResponse(builder.build());
  }
}
