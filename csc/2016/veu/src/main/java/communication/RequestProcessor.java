package communication;

import org.jetbrains.annotations.Nullable;

public interface RequestProcessor {
  @Nullable
  Protocol.ServerResponse.Builder processRequest(Protocol.ServerRequest request);
}
