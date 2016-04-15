package communication;

import org.jetbrains.annotations.Nullable;

public interface RequestProcessor {
  @Nullable
  Protocol.ServerResponse processRequest(Protocol.ServerRequest request);
}
