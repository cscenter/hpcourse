package communication;

import communication.Protocol.ServerRequest;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.Socket;

public class Util {
  @Nullable
  public static ServerRequest getRequest(Socket socket) throws IOException {
    int size = socket.getInputStream().read();
    if (size <= 0) {
      return null;
    }
    byte[] bytes = new byte[size];
    int read = socket.getInputStream().read(bytes);
    if (read <= 0) {
      throw new IOException("empty request");
    }
    return ServerRequest.parseFrom(bytes);
  }

  public static void sendResponse(Socket socket, Protocol.ServerResponse response) throws IOException {
    int responseSize = response.getSerializedSize();
    socket.getOutputStream().write(responseSize);
    response.writeTo(socket.getOutputStream());
  }
}
