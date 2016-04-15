package communication;

import communication.Protocol.ListTasksResponse;
import org.jetbrains.annotations.Nullable;

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
    ListTasksResponse listTasksResponse = ListTasksResponse.newBuilder()
      .addAllTasks(myStorage.getTasks())
      .setStatus(Protocol.Status.OK)
      .build();
    return Protocol.ServerResponse.newBuilder().setListResponse(listTasksResponse);
  }
}
