package communication;

import communication.Protocol.*;
import org.jetbrains.annotations.Nullable;

public class SubscribeProcessor implements RequestProcessor {
  private final Storage myStorage;

  public SubscribeProcessor(Storage storage) {
    myStorage = storage;
  }

  @Nullable
  public ServerResponse.Builder processRequest(Protocol.ServerRequest request) {
    if (!request.hasSubscribe()) {
      return null;
    }
    Protocol.Subscribe subscribe = request.getSubscribe();
    Long result = myStorage.getValue(subscribe.getTaskId());
    SubscribeResponse.Builder builder = SubscribeResponse.newBuilder();
    if (result == null) {
      builder.setStatus(Protocol.Status.ERROR);
    } else {
      builder.setStatus(Protocol.Status.OK).setValue(result);
    }
    SubscribeResponse subscribeResponse = builder.build();
    return ServerResponse.newBuilder().setSubscribeResponse(subscribeResponse);
  }
}
